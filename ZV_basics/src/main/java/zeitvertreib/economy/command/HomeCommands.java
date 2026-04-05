package zeitvertreib.economy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;

import zeitvertreib.economy.currency.CurrencyManager;
import zeitvertreib.economy.home.HomeManager;
import zeitvertreib.economy.home.PlayerHome;

public final class HomeCommands {
	private final CurrencyManager currencyManager;
	private final HomeManager homeManager;

	public HomeCommands(CurrencyManager currencyManager, HomeManager homeManager) {
		this.currencyManager = currencyManager;
		this.homeManager = homeManager;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
		dispatcher.register(Commands.literal("sethome")
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(this::setHome)));

		dispatcher.register(Commands.literal("home")
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(this::suggestHomeNames)
				.executes(this::goHome)));

		dispatcher.register(Commands.literal("delhome")
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(this::suggestHomeNames)
				.executes(this::deleteHome)));

		dispatcher.register(Commands.literal("homes")
			.executes(this::listHomes));
	}

	private int setHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		String key = name.toLowerCase(Locale.ROOT);
		int cost = HomeManager.SET_HOME_COST;

		PlayerHome existing = homeManager.getHome(context.getSource().getServer(), player.getUUID(), name);
		if (existing == null) {
			if (!currencyManager.withdraw(context.getSource().getServer(), player.getUUID(), cost)) {
				int balance = currencyManager.getBalance(context.getSource().getServer(), player.getUUID());
				context.getSource().sendFailure(prefix()
					.append(Component.literal("Setting a new home costs "))
					.append(formatCurrency(cost))
					.append(Component.literal(" but you only have "))
					.append(formatCurrency(balance))
					.append(Component.literal(".")));
				return 0;
			}
		}

		ServerLevel level = (ServerLevel) player.level();
		String dimension = level.dimension().identifier().toString();
		PlayerHome home = new PlayerHome(key, dimension,
			player.getX(), player.getY(), player.getZ(),
			player.getYRot(), player.getXRot());

		if (!homeManager.setHome(context.getSource().getServer(), player.getUUID(), name, home)) {
			if (existing == null) {
				currencyManager.addBalance(context.getSource().getServer(), player.getUUID(), cost);
			}
			int maxHomes = homeManager.getMaxHomes(context.getSource().getServer(), player.getUUID());
			context.getSource().sendFailure(prefix()
				.append(Component.literal("You have reached the maximum of " + maxHomes
					+ " homes. Delete one first with /delhome <name>. (Team level 5/10/15 unlocks more!)")));
			return 0;
		}

		if (existing != null) {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("Home '" + key + "' updated to your current location.")), false);
		} else {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("Home '" + key + "' set! Cost: "))
				.append(formatCurrency(cost))
				.append(Component.literal(".")), false);
		}

		return Command.SINGLE_SUCCESS;
	}

	private int goHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		String key = name.toLowerCase(Locale.ROOT);

		if (player.isCreative()) {
			context.getSource().sendFailure(prefix()
				.append(Component.literal("Creative mode players cannot use homes.")));
			return 0;
		}

		PlayerHome home = homeManager.getHome(context.getSource().getServer(), player.getUUID(), name);
		if (home == null) {
			context.getSource().sendFailure(prefix()
				.append(Component.literal("No home named '" + key + "'. Use /homes to see your homes.")));
			return 0;
		}

		if (homeManager.hasPendingTeleport(player.getUUID())
				|| zeitvertreib.economy.ZeitvertreibEconomy.TPA_MANAGER.hasPendingTeleport(player.getUUID())) {
			context.getSource().sendFailure(prefix()
				.append(Component.literal("You already have a pending teleport.")));
			return 0;
		}

		homeManager.registerPendingTeleport(context.getSource().getServer(), player.getUUID(), home);
		player.sendSystemMessage(prefix()
			.append(Component.literal("Stand still for 3 seconds to teleport to '" + home.name()
				+ "'. Moving resets the countdown.")));

		return Command.SINGLE_SUCCESS;
	}

	private int deleteHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		String key = name.toLowerCase(Locale.ROOT);

		if (!homeManager.deleteHome(context.getSource().getServer(), player.getUUID(), name)) {
			context.getSource().sendFailure(prefix()
				.append(Component.literal("No home named '" + key + "'.")));
			return 0;
		}

		context.getSource().sendSuccess(() -> prefix()
			.append(Component.literal("Home '" + key + "' deleted.")), false);
		return Command.SINGLE_SUCCESS;
	}

	private CompletableFuture<Suggestions> suggestHomeNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Map<String, PlayerHome> playerHomes = homeManager.getHomes(context.getSource().getServer(), player.getUUID());
		for (String homeName : playerHomes.keySet()) {
			builder.suggest(homeName);
		}
		return builder.buildFuture();
	}

	private int listHomes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Map<String, PlayerHome> playerHomes = homeManager.getHomes(context.getSource().getServer(), player.getUUID());

		if (playerHomes.isEmpty()) {
			context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("You have no homes. Use /sethome <name> to set one.")), false);
			return Command.SINGLE_SUCCESS;
		}

		int maxHomes = homeManager.getMaxHomes(context.getSource().getServer(), player.getUUID());
		context.getSource().sendSuccess(() -> prefix()
			.append(Component.literal("Your homes (" + playerHomes.size() + "/" + maxHomes + "):")), false);
		for (Map.Entry<String, PlayerHome> entry : playerHomes.entrySet()) {
			PlayerHome home = entry.getValue();
			String dimName = home.dimension().replace("minecraft:", "");
			context.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey())
				.withStyle(ChatFormatting.GREEN)
				.append(Component.literal(" — " + dimName + " @ "
					+ (int) home.x() + ", " + (int) home.y() + ", " + (int) home.z())
					.withStyle(ChatFormatting.GRAY)), false);
		}

		return Command.SINGLE_SUCCESS;
	}

	private static MutableComponent prefix() {
		return HomeManager.prefix();
	}

	private static MutableComponent formatCurrency(int amount) {
		return Component.literal(amount + " coins").withStyle(ChatFormatting.GOLD);
	}
}
