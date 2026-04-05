package zeitvertreib.economy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import zeitvertreib.economy.bounty.BountyManager;
import zeitvertreib.economy.currency.CurrencyManager;

public final class BountyCommands {
	private static final String CURRENCY_LABEL = "coins";
	private static final int MIN_BOUNTY = 10;
	private static final int BOSS_ANNOUNCEMENT_DURATION_MS = 5_000;
	private static ServerBossEvent activeBossEvent;
	private static long activeBossExpireAt;

	private final CurrencyManager currencyManager;
	private final BountyManager bountyManager;

	public BountyCommands(CurrencyManager currencyManager, BountyManager bountyManager) {
		this.currencyManager = currencyManager;
		this.bountyManager = bountyManager;
		ServerTickEvents.END_SERVER_TICK.register(this::tickBossBar);
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
		dispatcher.register(Commands.literal("bounty")
			.then(Commands.literal("place")
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("amount", IntegerArgumentType.integer(MIN_BOUNTY))
						.executes(this::placeBounty))))
			.then(Commands.literal("list")
				.executes(this::listBounties))
			.then(Commands.literal("check")
				.executes(this::checkOwnBounty)
				.then(Commands.argument("player", EntityArgument.player())
					.executes(this::checkPlayerBounty))));
	}

	private int placeBounty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer placer = context.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		int amount = IntegerArgumentType.getInteger(context, "amount");

		if (placer.getUUID().equals(target.getUUID())) {
			context.getSource().sendFailure(prefix()
				.append(Component.literal("You cannot place a bounty on yourself.")));
			return 0;
		}

		if (!currencyManager.withdraw(context.getSource().getServer(), placer.getUUID(), amount)) {
			int balance = currencyManager.getBalance(context.getSource().getServer(), placer.getUUID());
			context.getSource().sendFailure(prefix()
				.append(Component.literal("You need "))
				.append(formatCurrency(amount))
				.append(Component.literal(" but you only have "))
				.append(formatCurrency(balance))
				.append(Component.literal(".")));
			return 0;
		}

		bountyManager.addBounty(context.getSource().getServer(), target.getUUID(), amount);
		int totalBounty = bountyManager.getBounty(context.getSource().getServer(), target.getUUID());

		context.getSource().sendSuccess(() -> prefix()
			.append(Component.literal("Placed a "))
			.append(formatCurrency(amount))
			.append(Component.literal(" bounty on " + target.getName().getString()
				+ ". Total bounty: "))
			.append(formatCurrency(totalBounty))
			.append(Component.literal(".")), false);

		target.sendSystemMessage(prefix()
			.append(Component.literal("Someone placed a bounty on you! Your total bounty is now "))
			.append(formatCurrency(totalBounty))
			.append(Component.literal(". Watch your back!")));

		announceBountyBossBar(context.getSource().getServer(), target.getName().getString(), amount);

		return Command.SINGLE_SUCCESS;
	}

	private int listBounties(CommandContext<CommandSourceStack> context) {
		List<Map.Entry<UUID, Integer>> topBounties = bountyManager.getTopBounties(context.getSource().getServer(), 10);

		if (topBounties.isEmpty()) {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("No active bounties.")), false);
			return Command.SINGLE_SUCCESS;
		}

		context.getSource().sendSuccess(() -> prefix()
			.append(Component.literal("Top Bounties:")), false);

		int rank = 1;
		for (Map.Entry<UUID, Integer> entry : topBounties) {
			String playerName = resolvePlayerName(context, entry.getKey());
			int r = rank;
			context.getSource().sendSuccess(() -> Component.literal("  " + r + ". " + playerName + " — ")
				.withStyle(ChatFormatting.RED)
				.append(formatCurrency(entry.getValue())), false);
			rank++;
		}

		return Command.SINGLE_SUCCESS;
	}

	private int checkOwnBounty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int bounty = bountyManager.getBounty(context.getSource().getServer(), player.getUUID());

		if (bounty <= 0) {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("You have no bounty on your head.")), false);
		} else {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("Your bounty: "))
				.append(formatCurrency(bounty))
				.append(Component.literal(".")), false);
		}

		return Command.SINGLE_SUCCESS;
	}

	private int checkPlayerBounty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		int bounty = bountyManager.getBounty(context.getSource().getServer(), target.getUUID());

		String targetName = target.getName().getString();
		if (bounty <= 0) {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal(targetName + " has no bounty.")), false);
		} else {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal(targetName + "'s bounty: "))
				.append(formatCurrency(bounty))
				.append(Component.literal(".")), false);
		}

		return Command.SINGLE_SUCCESS;
	}

	private String resolvePlayerName(CommandContext<CommandSourceStack> context, UUID playerId) {
		MinecraftServer server = context.getSource().getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			return player.getName().getString();
		}

		return playerId.toString().substring(0, 8) + "...";
	}

	private void tickBossBar(MinecraftServer server) {
		if (activeBossEvent == null) return;
		if (System.currentTimeMillis() >= activeBossExpireAt) {
			removeActiveBossEvent();
		}
	}

	private void announceBountyBossBar(MinecraftServer server, String targetName, int amount) {
		removeActiveBossEvent();
		activeBossEvent = new ServerBossEvent(
			Component.literal("Bounty placed: " + amount + " coins on " + targetName + "!")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
			BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.PROGRESS
		);
		activeBossEvent.setProgress(1.0f);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			activeBossEvent.addPlayer(player);
		}
		activeBossExpireAt = System.currentTimeMillis() + BOSS_ANNOUNCEMENT_DURATION_MS;
	}

	private static void removeActiveBossEvent() {
		if (activeBossEvent != null) {
			activeBossEvent.removeAllPlayers();
			activeBossEvent = null;
		}
	}

	private static MutableComponent prefix() {
		return Component.literal("[Bounty] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
	}

	private static MutableComponent formatCurrency(int amount) {
		return Component.literal(amount + " " + CURRENCY_LABEL).withStyle(ChatFormatting.GOLD);
	}
}
