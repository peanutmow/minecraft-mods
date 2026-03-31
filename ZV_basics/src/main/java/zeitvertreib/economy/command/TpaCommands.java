package zeitvertreib.economy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import zeitvertreib.economy.currency.CurrencyManager;
import zeitvertreib.economy.tpa.PendingTeleport;
import zeitvertreib.economy.tpa.TpaManager;
import zeitvertreib.economy.tpa.TpaRequest;

public final class TpaCommands {
	private static final String CURRENCY_LABEL = "coins";

	private final CurrencyManager currencyManager;
	private final TpaManager tpaManager;

	public TpaCommands(CurrencyManager currencyManager, TpaManager tpaManager) {
		this.currencyManager = currencyManager;
		this.tpaManager = tpaManager;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
		// /tpa accept, /tpa deny must come before the player argument to take priority.
		dispatcher.register(Commands.literal("tpa")
			.then(Commands.literal("accept").executes(this::acceptRequest))
			.then(Commands.literal("deny").executes(this::denyRequest))
			.then(Commands.argument("player", EntityArgument.player()).executes(this::sendGotoRequest)));

		dispatcher.register(Commands.literal("tpahere")
			.then(Commands.argument("player", EntityArgument.player()).executes(this::sendComeRequest)));
	}

	private int sendGotoRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return sendRequest(context, TpaRequest.TpaType.GOTO);
	}

	private int sendComeRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return sendRequest(context, TpaRequest.TpaType.COME);
	}

	private int sendRequest(CommandContext<CommandSourceStack> context, TpaRequest.TpaType type) throws CommandSyntaxException {
		ServerPlayer requester = context.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(context, "player");

		if (requester.getUUID().equals(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("You cannot send a TPA request to yourself."));
			return 0;
		}

		long remainingCooldown = tpaManager.getRemainingCooldownMillis(requester.getUUID());
		if (remainingCooldown > 0) {
			context.getSource().sendFailure(prefix()
				.append(Component.literal("You must wait "))
				.append(Component.literal(formatDuration(remainingCooldown)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" before sending another TPA request.")));
			return 0;
		}

		int cost = tpaManager.computeCost(context.getSource().getServer(), currencyManager);
		if (!currencyManager.hasBalance(context.getSource().getServer(), requester.getUUID(), cost)) {
			int balance = currencyManager.getBalance(context.getSource().getServer(), requester.getUUID());
			context.getSource().sendFailure(prefix()
				.append(Component.literal("Teleportation costs "))
				.append(formatCurrency(cost))
				.append(Component.literal(" but you only have "))
				.append(formatCurrency(balance))
				.append(Component.literal(".")));
			return 0;
		}

		tpaManager.sendRequest(requester.getUUID(), target.getUUID(), requester.getName().getString(), target.getName().getString(), type);

		String requestDesc = type == TpaRequest.TpaType.GOTO
			? requester.getName().getString() + " wants to teleport to you."
			: requester.getName().getString() + " wants you to teleport to them.";

		context.getSource().sendSuccess(() -> prefix()
			.append(Component.literal("Sent a TPA request to " + target.getName().getString() + ". Cost: "))
			.append(formatCurrency(cost))
			.append(Component.literal(". Expires in 60 seconds.")), false);

		target.sendSystemMessage(prefix()
			.append(Component.literal(requestDesc + " "))
			.append(actionButton("Accept", ChatFormatting.GREEN, "/tpa accept", "Accept the teleport request"))
			.append(Component.literal(" "))
			.append(actionButton("Deny", ChatFormatting.RED, "/tpa deny", "Deny the teleport request")));

		return Command.SINGLE_SUCCESS;
	}

	private int acceptRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer accepter = context.getSource().getPlayerOrException();
		TpaRequest request = tpaManager.getIncomingRequest(accepter.getUUID());

		if (request == null) {
			context.getSource().sendFailure(prefix().append(Component.literal("You have no pending TPA request.")));
			return 0;
		}

		ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayer(request.requesterId());
		if (requester == null) {
			tpaManager.removeRequest(request);
			context.getSource().sendFailure(prefix().append(Component.literal(request.requesterName() + " is no longer online.")));
			return 0;
		}

		// Re-compute cost at accept time to reflect any economy changes since the request was sent.
		int cost = tpaManager.computeCost(context.getSource().getServer(), currencyManager);
		if (!currencyManager.withdraw(context.getSource().getServer(), requester.getUUID(), cost)) {
			tpaManager.removeRequest(request);
			int requesterBalance = currencyManager.getBalance(context.getSource().getServer(), requester.getUUID());
			context.getSource().sendFailure(prefix()
				.append(Component.literal(request.requesterName() + " can no longer afford the cost (")));
			requester.sendSystemMessage(prefix()
				.append(Component.literal("Your TPA request to " + request.targetName() + " was accepted, but you can no longer afford the cost of "))
				.append(formatCurrency(cost))
				.append(Component.literal(". Your balance: "))
				.append(formatCurrency(requesterBalance))
				.append(Component.literal(".")));
			return 0;
		}

		tpaManager.removeRequest(request);

		ServerPlayer teleportingPlayer;
		ServerPlayer destination;
		if (request.type() == TpaRequest.TpaType.GOTO) {
			// Requester goes to accepter.
			teleportingPlayer = requester;
			destination = accepter;
		} else {
			// Accepter goes to requester.
			teleportingPlayer = accepter;
			destination = requester;
		}

		if (tpaManager.hasPendingTeleport(teleportingPlayer.getUUID())) {
			// Refund and block — the teleporting player already has a warmup running.
			currencyManager.addBalance(context.getSource().getServer(), request.requesterId(), cost);
			context.getSource().sendFailure(prefix().append(
				Component.literal(teleportingPlayer.getName().getString() + " already has a pending teleport.")));
			return 0;
		}

		long executeAt = System.currentTimeMillis() + PendingTeleport.WARMUP_MILLIS;
		tpaManager.registerPendingTeleport(context.getSource().getServer(), new PendingTeleport(
			teleportingPlayer.getUUID(),
			destination.getUUID(),
			request.requesterId(),
			teleportingPlayer.getName().getString(),
			destination.getName().getString(),
			teleportingPlayer.getX(),
			teleportingPlayer.getY(),
			teleportingPlayer.getZ(),
			executeAt,
			cost
		));

		teleportingPlayer.sendSystemMessage(prefix()
			.append(Component.literal("Stand still for 3 seconds to teleport to " + destination.getName().getString() + ". Cost: "))
			.append(formatCurrency(cost))
			.append(Component.literal(". Moving resets the countdown.")));

		return Command.SINGLE_SUCCESS;
	}

	private int denyRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer denier = context.getSource().getPlayerOrException();
		TpaRequest request = tpaManager.getIncomingRequest(denier.getUUID());

		if (request == null) {
			context.getSource().sendFailure(prefix().append(Component.literal("You have no pending TPA request.")));
			return 0;
		}

		tpaManager.removeRequest(request);
		context.getSource().sendSuccess(() -> prefix()
			.append(Component.literal("Denied the TPA request from " + request.requesterName() + ".")), false);

		ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayer(request.requesterId());
		if (requester != null) {
			requester.sendSystemMessage(prefix()
				.append(Component.literal(request.targetName() + " denied your TPA request.")));
		}

		return Command.SINGLE_SUCCESS;
	}

	private static MutableComponent prefix() {
		return Component.literal("[TPA] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	}

	private static MutableComponent actionButton(String label, ChatFormatting color, String command, String hoverText) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(color)
			.withBold(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
	}

	private static MutableComponent formatCurrency(int amount) {
		return Component.literal(amount + " " + CURRENCY_LABEL).withStyle(ChatFormatting.GOLD);
	}

	private static String formatDuration(long durationMillis) {
		long totalSeconds = Math.max(1L, (durationMillis + 999L) / 1000L);
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		if (minutes == 0L) {
			return seconds + "s";
		}
		return minutes + "m " + seconds + "s";
	}
}
