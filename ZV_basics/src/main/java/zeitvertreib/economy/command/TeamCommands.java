package zeitvertreib.economy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import zeitvertreib.economy.currency.CurrencyManager;
import zeitvertreib.economy.team.TeamData;
import zeitvertreib.economy.team.TeamInvite;
import zeitvertreib.economy.team.TeamManager;

public final class TeamCommands {
	private static final String CURRENCY_LABEL = "coins";

	private final CurrencyManager currencyManager;
	private final TeamManager teamManager;

	public TeamCommands(CurrencyManager currencyManager, TeamManager teamManager) {
		this.currencyManager = currencyManager;
		this.teamManager = teamManager;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
		dispatcher.register(Commands.literal("zv")
			.then(buildTeamCommandRoot()));
		dispatcher.register(buildBankCommandRoot());
		dispatcher.register(buildTeamsCommandRoot());
		dispatcher.register(Commands.literal("tc")
			.then(Commands.argument("message", StringArgumentType.greedyString())
				.executes(this::teamChat)));
	}

	private LiteralArgumentBuilder<CommandSourceStack> buildTeamCommandRoot() {
		return Commands.literal("team")
			.then(Commands.literal("create")
				.then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("color", StringArgumentType.word())
						.executes(this::createTeam))))
			.then(Commands.literal("info")
				.executes(this::teamInfo))
			.then(Commands.literal("leave")
				.executes(this::leaveTeam))
			.then(Commands.literal("invite")
				.then(Commands.argument("player", EntityArgument.player())
					.executes(this::invitePlayer)))
			.then(Commands.literal("accept")
				.then(Commands.argument("team", StringArgumentType.word())
					.executes(this::acceptInvite)))
			.then(Commands.literal("deny")
				.then(Commands.argument("team", StringArgumentType.word())
					.executes(this::denyInvite)))
			.then(Commands.literal("kick")
				.then(Commands.argument("player", EntityArgument.player())
					.executes(this::kickPlayer)))
			.then(Commands.literal("transfer")
				.then(Commands.argument("player", EntityArgument.player())
					.executes(this::transferLeadership)))
			.then(Commands.literal("modify")
				.then(Commands.literal("rename")
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(this::renameTeam)))
				.then(Commands.literal("color")
					.then(Commands.argument("color", StringArgumentType.word())
						.executes(this::recolorTeam))))
			.then(Commands.literal("bank")
				.then(Commands.literal("deposit")						.then(Commands.literal("all")
							.executes(this::depositAllToBank))					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(this::depositToBank)))
				.then(Commands.literal("withdraw")
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(this::withdrawFromBank)))
				.then(Commands.literal("limit")
					.then(Commands.argument("percentage", IntegerArgumentType.integer(1, 100))
						.suggests(this::suggestLimitPercent)
						.then(Commands.argument("minutes", IntegerArgumentType.integer(1))
							.suggests(this::suggestLimitMinutes)
									.executes(this::setBankLimit))))
				.then(Commands.literal("block")
					.then(Commands.literal("withdraw")
						.then(Commands.literal("all")
							.executes(this::blockWithdrawalsAll))
						.then(Commands.argument("player", EntityArgument.player())
							.executes(this::blockWithdrawalsPlayer))))
				.then(Commands.literal("unblock")
					.then(Commands.literal("withdraw")
						.then(Commands.literal("all")
							.executes(this::unblockWithdrawalsAll))
						.then(Commands.argument("player", EntityArgument.player())
							.executes(this::unblockWithdrawalsPlayer))))
						)
		.then(Commands.literal("levelup")
				.executes(this::levelUpTeam))
			.then(Commands.literal("list")
				.executes(this::showTeamsList))
			.then(Commands.literal("ranked")
				.executes(this::showRankedTeams));
	}

	private LiteralArgumentBuilder<CommandSourceStack> buildTeamsCommandRoot() {
		return Commands.literal("teams")
			.executes(this::showTeamsList)
			.then(Commands.literal("ranked")
				.executes(this::showRankedTeams));
	}

	private LiteralArgumentBuilder<CommandSourceStack> buildBankCommandRoot() {
		return Commands.literal("bank")
			.then(Commands.literal("block")
				.then(Commands.literal("withdraw")
					.then(Commands.literal("all")
						.executes(this::blockWithdrawalsAll))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(this::blockWithdrawalsPlayer))))
			.then(Commands.literal("unblock")
				.then(Commands.literal("withdraw")
					.then(Commands.literal("all")
						.executes(this::unblockWithdrawalsAll))
					.then(Commands.argument("player", EntityArgument.player())
						.executes(this::unblockWithdrawalsPlayer))))
			.then(Commands.literal("deposit")
				.then(Commands.literal("all")
					.executes(this::depositAllToBank))
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
					.executes(this::depositToBank)))
			.then(Commands.literal("withdraw")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
					.executes(this::withdrawFromBank)))
			.then(Commands.literal("limit")
				.then(Commands.argument("percentage", IntegerArgumentType.integer(1, 100))
					.suggests(this::suggestLimitPercent)
					.then(Commands.argument("minutes", IntegerArgumentType.integer(1))
						.suggests(this::suggestLimitMinutes)
						.executes(this::setBankLimit))))
		;
	}

	private int createTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String rawName = StringArgumentType.getString(context, "name");
		ChatFormatting color = parseColor(StringArgumentType.getString(context, "color"));
		if (color == null) {
			context.getSource().sendFailure(Component.literal("Invalid team color. Use a vanilla text color, e.g. " + listSupportedColors() + "."));
			return 0;
		}

		String normalizedName = TeamManager.normalizeTeamName(rawName);
		if (teamManager.getTeam(context.getSource().getServer(), normalizedName) != null) {
			context.getSource().sendFailure(Component.literal("That team name is already taken."));
			return 0;
		}

		TeamData team = teamManager.createTeam(context.getSource().getServer(), player, normalizedName, color);
		MutableComponent message = Component.literal("Created team ")
			.append(teamManager.describeTeam(team))
			.append(Component.literal(". You are now the team leader. Level: " + team.level() + ". Member slots: " + team.maxMembers() + "."));
		context.getSource().sendSuccess(() -> message, false);
		return Command.SINGLE_SUCCESS;
	}

	private int invitePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		if (leader.getUUID().equals(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("You cannot invite yourself."));
			return 0;
		}

		if (team.hasMember(target.getUUID())) {
			context.getSource().sendFailure(Component.literal(target.getName().getString() + " is already in your team."));
			return 0;
		}

		if (teamManager.getTeamForPlayer(context.getSource().getServer(), target.getUUID()) != null) {
			context.getSource().sendFailure(Component.literal(target.getName().getString() + " is already in another team."));
			return 0;
		}

		if (teamManager.getAvailableInviteSlots(context.getSource().getServer(), team) <= 0) {
			context.getSource().sendFailure(Component.literal("Your team has no free member slots. Upgrade it with /zv team levelup first."));
			return 0;
		}

		teamManager.invitePlayer(context.getSource().getServer(), team, leader, target);
		context.getSource().sendSuccess(() -> Component.literal("Sent a team invite to " + target.getName().getString() + "."), false);
		target.sendSystemMessage(buildInviteMessage(team, leader.getName().getString()));
		return Command.SINGLE_SUCCESS;
	}

	private int acceptInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		if (teamManager.getTeamForPlayer(context.getSource().getServer(), player.getUUID()) != null) {
			context.getSource().sendFailure(Component.literal("Leave your current team before accepting another invite."));
			return 0;
		}

		String teamName = TeamManager.normalizeTeamName(StringArgumentType.getString(context, "team"));
		TeamInvite invite = teamManager.getInvite(context.getSource().getServer(), player.getUUID());
		if (invite == null || !invite.teamName().equals(teamName)) {
			context.getSource().sendFailure(Component.literal("You do not have a pending invite for that team."));
			return 0;
		}

		TeamData invitedTeam = teamManager.getTeam(context.getSource().getServer(), teamName);
		if (invitedTeam == null) {
			context.getSource().sendFailure(Component.literal("That team no longer exists."));
			return 0;
		}

		if (!teamManager.canAcceptNewMember(context.getSource().getServer(), invitedTeam)) {
			context.getSource().sendFailure(Component.literal("That team is full right now. Ask the leader to use /zv team levelup."));
			return 0;
		}

		TeamData team = teamManager.acceptInvite(context.getSource().getServer(), player, teamName);
		if (team == null) {
			context.getSource().sendFailure(Component.literal("That invite is no longer valid."));
			return 0;
		}

		MutableComponent joinedMessage = Component.literal("You joined team ").append(teamManager.describeTeam(team)).append(Component.literal("."));
		context.getSource().sendSuccess(() -> joinedMessage, false);

		ServerPlayer leader = context.getSource().getServer().getPlayerList().getPlayer(team.leaderId());
		if (leader != null && !leader.getUUID().equals(player.getUUID())) {
			leader.sendSystemMessage(Component.literal(player.getName().getString() + " joined your team ").append(teamManager.describeTeam(team)).append(Component.literal(".")));
		}
		return Command.SINGLE_SUCCESS;
	}

	private int denyInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String teamName = TeamManager.normalizeTeamName(StringArgumentType.getString(context, "team"));
		TeamInvite invite = teamManager.denyInvite(context.getSource().getServer(), player.getUUID(), teamName);
		if (invite == null) {
			context.getSource().sendFailure(Component.literal("You do not have a pending invite for that team."));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("Denied the invite to [" + invite.teamName() + "]."), false);
		TeamData team = teamManager.getTeam(context.getSource().getServer(), invite.teamName());
		if (team != null) {
			ServerPlayer leader = context.getSource().getServer().getPlayerList().getPlayer(team.leaderId());
			if (leader != null && !leader.getUUID().equals(player.getUUID())) {
				leader.sendSystemMessage(Component.literal(player.getName().getString() + " denied your invite to ").append(teamManager.describeTeam(team)).append(Component.literal(".")));
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	private int kickPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		if (leader.getUUID().equals(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("Use leadership transfer before removing yourself from the team."));
			return 0;
		}

		if (!team.hasMember(target.getUUID())) {
			context.getSource().sendFailure(Component.literal(target.getName().getString() + " is not in your team."));
			return 0;
		}

		teamManager.kickMember(context.getSource().getServer(), team, target.getUUID());
		context.getSource().sendSuccess(() -> Component.literal("Removed " + target.getName().getString() + " from ").append(teamManager.describeTeam(team)).append(Component.literal(".")), false);
		target.sendSystemMessage(Component.literal("You were removed from team ").append(teamManager.describeTeam(team)).append(Component.literal(".")));
		return Command.SINGLE_SUCCESS;
	}

	private int transferLeadership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		if (leader.getUUID().equals(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("You already lead this team."));
			return 0;
		}

		if (!team.hasMember(target.getUUID())) {
			context.getSource().sendFailure(Component.literal(target.getName().getString() + " is not in your team."));
			return 0;
		}

		teamManager.transferLeadership(context.getSource().getServer(), team, target.getUUID());
		context.getSource().sendSuccess(() -> Component.literal("Transferred team leadership to " + target.getName().getString() + "."), false);
		target.sendSystemMessage(Component.literal("You are now the leader of team ").append(teamManager.describeTeam(team)).append(Component.literal(".")));
		return Command.SINGLE_SUCCESS;
	}

	private int renameTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		String rawName = StringArgumentType.getString(context, "name");
		if (!rawName.matches("(?i)[a-z]{1,5}")) {
			context.getSource().sendFailure(Component.literal("Team names must be letters only and 5 characters max."));
			return 0;
		}

		String normalizedName = TeamManager.normalizeTeamName(rawName);
		if (normalizedName.equals(team.name())) {
			context.getSource().sendSuccess(() -> Component.literal("Your team name is already " + team.name() + "."), false);
			return Command.SINGLE_SUCCESS;
		}

		if (teamManager.getTeam(context.getSource().getServer(), normalizedName) != null) {
			context.getSource().sendFailure(Component.literal("That team name is already taken."));
			return 0;
		}

		if (!teamManager.renameTeam(context.getSource().getServer(), team, rawName)) {
			context.getSource().sendFailure(Component.literal("Unable to rename the team. Please try again."));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("Team renamed to ").append(Component.literal(team.name()).withStyle(team.color())), false);
		return Command.SINGLE_SUCCESS;
	}

	private int recolorTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		ChatFormatting color = parseColor(StringArgumentType.getString(context, "color"));
		if (color == null) {
			context.getSource().sendFailure(Component.literal("Invalid team color. Use a vanilla text color, e.g. " + listSupportedColors() + "."));
			return 0;
		}

		if (!teamManager.recolorTeam(context.getSource().getServer(), team, color)) {
			context.getSource().sendFailure(Component.literal("Unable to recolor the team. Please try again."));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("Team recolored to ").append(Component.literal(color.getName()).withStyle(color)), false);
		return Command.SINGLE_SUCCESS;
	}

	private int depositToBank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TeamData team = requirePlayerTeam(context, player);
		if (team == null) {
			return 0;
		}

		int requestedAmount = IntegerArgumentType.getInteger(context, "amount");
		int capacityRemaining = team.maxBankCapacity() - team.bankBalance();
		if (capacityRemaining <= 0) {
			context.getSource().sendFailure(Component.literal("Your team bank is already full."));
			return 0;
		}

		int depositAmount = Math.min(requestedAmount, capacityRemaining);
		if (!currencyManager.withdraw(context.getSource().getServer(), player.getUUID(), depositAmount)) {
			context.getSource().sendFailure(Component.literal("You do not have enough coins to deposit that amount."));
			return 0;
		}

		if (!teamManager.depositToBank(context.getSource().getServer(), team, depositAmount, player.getUUID())) {
			context.getSource().sendFailure(Component.literal("Team bank deposit would exceed your level limit (max ")
				.append(Component.literal(String.valueOf(team.maxBankCapacity())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(").")));
			return 0;
		}
		notifyLeaderOfBankTransfer(context.getSource().getServer(), team, player, "deposited", depositAmount);

		MutableComponent response = Component.literal("Deposited ")
			.append(formatCurrency(depositAmount));
		if (depositAmount < requestedAmount) {
			response.append(Component.literal(" of your requested "))
				.append(formatCurrency(requestedAmount));
		}
		response.append(Component.literal(" into team bank. New team bank balance: "))
			.append(formatCurrency(team.bankBalance()))
			.append(Component.literal(". Max is "))
			.append(formatCurrency(team.maxBankCapacity()))
			.append(Component.literal("."));
		context.getSource().sendSuccess(() -> response, false);
		return Command.SINGLE_SUCCESS;
	}

	private int depositAllToBank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TeamData team = requirePlayerTeam(context, player);
		if (team == null) {
			return 0;
		}

		int capacityRemaining = team.maxBankCapacity() - team.bankBalance();
		if (capacityRemaining <= 0) {
			context.getSource().sendFailure(Component.literal("Your team bank is already full."));
			return 0;
		}

		int playerBalance = currencyManager.getBalance(context.getSource().getServer(), player.getUUID());
		if (playerBalance <= 0) {
			context.getSource().sendFailure(Component.literal("You have no coins to deposit."));
			return 0;
		}

		int depositAmount = Math.min(playerBalance, capacityRemaining);
		if (!currencyManager.withdraw(context.getSource().getServer(), player.getUUID(), depositAmount)) {
			context.getSource().sendFailure(Component.literal("Unable to withdraw coins for deposit."));
			return 0;
		}

		if (!teamManager.depositToBank(context.getSource().getServer(), team, depositAmount, player.getUUID())) {
			currencyManager.addBalance(context.getSource().getServer(), player.getUUID(), depositAmount);
			context.getSource().sendFailure(Component.literal("Team bank deposit failed. Please try again."));
			return 0;
		}

		notifyLeaderOfBankTransfer(context.getSource().getServer(), team, player, "deposited", depositAmount);
		MutableComponent response = Component.literal("Deposited ")
			.append(formatCurrency(depositAmount))
			.append(Component.literal(" into team bank. New team bank balance: "))
			.append(formatCurrency(team.bankBalance()))
			.append(Component.literal(". Max is "))
			.append(formatCurrency(team.maxBankCapacity()))
			.append(Component.literal("."));
		context.getSource().sendSuccess(() -> response, false);
		return Command.SINGLE_SUCCESS;
}

	private int levelUpTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		int targetLevel = team.level() + 1;
		int upgradeCost = teamManager.getLevelUpCost(team);
		if (!teamManager.levelUpTeam(context.getSource().getServer(), team)) {
			context.getSource().sendFailure(Component.literal("Your team bank needs ")
				.append(formatCurrency(upgradeCost))
				.append(Component.literal(" to reach level " + targetLevel + ".")));
			return 0;
		}

		notifyTeamUpgrade(context.getSource().getServer(), team);

		MutableComponent response = Component.literal("Upgraded ")
			.append(teamManager.describeTeam(team))
			.append(Component.literal(" to level " + team.level() + ". Member slots: "))
			.append(Component.literal(String.valueOf(team.maxMembers())).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(". Bank remaining: "))
			.append(formatCurrency(team.bankBalance()))
			.append(Component.literal(". Next upgrade cost: "))
			.append(formatCurrency(teamManager.getLevelUpCost(team)))
			.append(Component.literal("."));
		context.getSource().sendSuccess(() -> response, false);
		return Command.SINGLE_SUCCESS;
	}

	private void notifyTeamUpgrade(MinecraftServer server, TeamData team) {
		for (UUID memberId : team.memberIds()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			if (member == null) {
				continue;
			}

			member.sendSystemMessage(Component.literal("Your team has been upgraded!").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" New level: " + team.level()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(". Member slots: " ).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(String.valueOf(team.maxMembers())).withStyle(team.color()))
				.append(Component.literal(". Bank capacity: " ).withStyle(ChatFormatting.GRAY))
				.append(formatCurrency(team.maxBankCapacity())));
		}
	}

	private int withdrawFromBank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TeamData team = requirePlayerTeam(context, player);
		if (team == null) {
			return 0;
		}

		int amount = IntegerArgumentType.getInteger(context, "amount");
		long now = System.currentTimeMillis();
		if (team.isWithdrawalsRestricted(player.getUUID())) {
			context.getSource().sendFailure(Component.literal("Team withdrawals are currently blocked for you."));
			return 0;
		}
		long cooldown = team.withdrawCooldownRemaining(player.getUUID(), now);
		int limitAmount = team.maxWithdrawAmountForPlayer(player.getUUID());
		if (cooldown > 0L) {
			long secondsRemaining = (cooldown + 999L) / 1000L;
			context.getSource().sendFailure(Component.literal("You must wait ")
				.append(Component.literal(secondsRemaining + " seconds").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" before withdrawing again. Max withdrawal per " + team.withdrawalLimitIntervalMinutes() + " minutes is "))
				.append(formatCurrency(limitAmount))
				.append(Component.literal(".")));
			return 0;
		}
		if (amount > limitAmount) {
			context.getSource().sendFailure(Component.literal("You can only withdraw up to ")
				.append(formatCurrency(limitAmount))
				.append(Component.literal(" per " + team.withdrawalLimitIntervalMinutes() + " minutes (" + team.withdrawalLimitPercent() + "% of the bank).")));
			return 0;
		}
		if (!teamManager.withdrawFromBank(context.getSource().getServer(), team, amount, player.getUUID())) {
			context.getSource().sendFailure(Component.literal("Your team bank does not have enough coins."));
			return 0;
		}

		notifyLeaderOfBankTransfer(context.getSource().getServer(), team, player, "withdrew", amount);
		currencyManager.addBalance(context.getSource().getServer(), player.getUUID(), amount);
		MutableComponent response = Component.literal("Withdrew ")
			.append(formatCurrency(amount))
			.append(Component.literal(" from team bank. New team bank balance: "))
			.append(formatCurrency(team.bankBalance()))
			.append(Component.literal("."));
		context.getSource().sendSuccess(() -> response, false);
		return Command.SINGLE_SUCCESS;
	}

	private void notifyLeaderOfBankTransfer(MinecraftServer server, TeamData team, ServerPlayer player, String action, int amount) {
		ServerPlayer leader = server.getPlayerList().getPlayer(team.leaderId());
		if (leader == null || leader.getUUID().equals(player.getUUID())) {
			return;
		}

		leader.sendSystemMessage(Component.literal("[Team Bank] ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(player.getName().getString()).withStyle(team.color()))
			.append(Component.literal(" " + action + " "))
			.append(formatCurrency(amount))
			.append(Component.literal(". New team bank balance: "))
			.append(formatCurrency(team.bankBalance()))
			.append(Component.literal(".")));
	}

	private int blockWithdrawalsAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		teamManager.setWithdrawalsBlockedForAll(context.getSource().getServer(), team, true);
		context.getSource().sendSuccess(() -> Component.literal("Withdrawals have been blocked for all non-leaders."), false);
		return Command.SINGLE_SUCCESS;
	}

	private int unblockWithdrawalsAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		teamManager.setWithdrawalsBlockedForAll(context.getSource().getServer(), team, false);
		context.getSource().sendSuccess(() -> Component.literal("Withdrawals have been unblocked for all team members."), false);
		return Command.SINGLE_SUCCESS;
	}

	private int blockWithdrawalsPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		if (!team.hasMember(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("That player is not in your team."));
			return 0;
		}

		if (team.leaderId().equals(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("You cannot block the leader."));
			return 0;
		}

		teamManager.setBlockedWithdrawalForPlayer(context.getSource().getServer(), team, target.getUUID(), true);
		context.getSource().sendSuccess(() -> Component.literal("Blocked withdrawals for ").append(Component.literal(target.getName().getString()).withStyle(team.color())).append(Component.literal(".")), false);
		return Command.SINGLE_SUCCESS;
	}

	private int unblockWithdrawalsPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		if (!team.hasMember(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("That player is not in your team."));
			return 0;
		}

		teamManager.setBlockedWithdrawalForPlayer(context.getSource().getServer(), team, target.getUUID(), false);
		context.getSource().sendSuccess(() -> Component.literal("Unblocked withdrawals for ").append(Component.literal(target.getName().getString()).withStyle(team.color())).append(Component.literal(".")), false);
		return Command.SINGLE_SUCCESS;
	}

	private int setBankLimit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer leader = context.getSource().getPlayerOrException();
		TeamData team = requireLeaderTeam(context, leader);
		if (team == null) {
			return 0;
		}

		int percentage = IntegerArgumentType.getInteger(context, "percentage");
		int minutes = IntegerArgumentType.getInteger(context, "minutes");
		if (!teamManager.setBankWithdrawLimit(context.getSource().getServer(), team, percentage, minutes)) {
			context.getSource().sendFailure(Component.literal("Invalid withdraw limit. Percentage must be 1-100 and minutes must be at least 1."));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("Set withdraw limit to ")
			.append(Component.literal(percentage + "% every " + minutes + " minutes").withStyle(ChatFormatting.GOLD))
			.append(Component.literal(". Players can withdraw up to " + percentage + "% of the team bank every " + minutes + " minutes.").withStyle(ChatFormatting.GRAY)), false);
		return Command.SINGLE_SUCCESS;
	}

	private CompletableFuture<Suggestions> suggestLimitPercent(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
		TeamData team = teamManager.getTeamForPlayer(context.getSource().getServer(), context.getSource().getPlayerOrException().getUUID());
		int current = team != null ? team.withdrawalLimitPercent() : 33;
		builder.suggest(String.valueOf(current), Component.literal("% of total team bank").withStyle(ChatFormatting.GRAY));
		builder.suggest("25", Component.literal("% of total team bank").withStyle(ChatFormatting.GRAY));
		builder.suggest("50", Component.literal("% of total team bank").withStyle(ChatFormatting.GRAY));
		builder.suggest("75", Component.literal("% of total team bank").withStyle(ChatFormatting.GRAY));
		builder.suggest("100", Component.literal("% of total team bank").withStyle(ChatFormatting.GRAY));
		return builder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestLimitMinutes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
		TeamData team = teamManager.getTeamForPlayer(context.getSource().getServer(), context.getSource().getPlayerOrException().getUUID());
		int current = team != null ? team.withdrawalLimitIntervalMinutes() : 5;
		builder.suggest(String.valueOf(current), Component.literal("withdrawal cooldown").withStyle(ChatFormatting.GRAY));
		builder.suggest("1", Component.literal("withdrawal cooldown").withStyle(ChatFormatting.GRAY));
		builder.suggest("5", Component.literal("withdrawal cooldown").withStyle(ChatFormatting.GRAY));
		builder.suggest("10", Component.literal("withdrawal cooldown").withStyle(ChatFormatting.GRAY));
		builder.suggest("15", Component.literal("withdrawal cooldown").withStyle(ChatFormatting.GRAY));
		builder.suggest("30", Component.literal("withdrawal cooldown").withStyle(ChatFormatting.GRAY));
		return builder.buildFuture();
	}

	private MutableComponent formatContribution(int amount) {
		String sign = amount >= 0 ? "+" : "";
		return Component.literal(sign + amount).withStyle(amount >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private TeamData requirePlayerTeam(CommandContext<CommandSourceStack> context, ServerPlayer player) {
		TeamData team = teamManager.getTeamForPlayer(context.getSource().getServer(), player.getUUID());
		if (team == null) {
			context.getSource().sendFailure(Component.literal("You are not in a team."));
		}
		return team;
	}

	private int teamInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TeamData team = requirePlayerTeam(context, player);
		if (team == null) {
			return 0;
		}

		var server = context.getSource().getServer();
		ServerPlayer leader = server.getPlayerList().getPlayer(team.leaderId());
		String leaderName = leader != null ? leader.getName().getString() : team.leaderId().toString();

		MutableComponent members = Component.literal("");
		boolean firstMember = true;
		for (UUID memberId : team.memberIds()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			String name = member != null ? member.getName().getString() : memberId.toString();
			if (!firstMember) {
				members.append(Component.literal("\n"));
			}
			firstMember = false;
			members.append(Component.literal(name + " "))
				.append(formatContribution(team.getContribution(memberId)));
		}

		MutableComponent message = Component.literal("Team: ")
			.append(teamManager.describeTeam(team))
			.append(Component.literal("\nLevel: " + team.level()))
			.append(Component.literal("\nLeader: " + leaderName))
			.append(Component.literal("\nMembers: " + team.memberIds().size() + "/" + team.maxMembers()))
			.append(Component.literal("\n"))
			.append(members)
			.append(Component.literal("\nBank: "))
			.append(formatCurrency(team.bankBalance()))
			.append(Component.literal("\nNext upgrade cost: "))
			.append(formatCurrency(teamManager.getLevelUpCost(team)))
			.append(Component.literal("\nWithdraw limit: " + team.withdrawalLimitPercent() + "% every " + team.withdrawalLimitIntervalMinutes() + " minutes"));

		if (team.areWithdrawalsBlockedForAll()) {
			message.append(Component.literal("\nWithdrawals: blocked for everyone except the leader"));
		} else if (!team.blockedWithdrawalMembers().isEmpty()) {
			message.append(Component.literal("\nWithdrawals blocked for: "));
			boolean first = true;
			for (UUID blockedMemberId : team.blockedWithdrawalMembers()) {
				ServerPlayer blockedMember = context.getSource().getServer().getPlayerList().getPlayer(blockedMemberId);
				String blockedName = blockedMember != null ? blockedMember.getName().getString() : blockedMemberId.toString();
				if (!first) {
					message.append(Component.literal(", "));
				}
				first = false;
				message.append(Component.literal(blockedName).withStyle(team.color()));
			}
		}

		context.getSource().sendSuccess(() -> message, false);
		return Command.SINGLE_SUCCESS;
	}

	private int showTeamsList(CommandContext<CommandSourceStack> context) {
		List<TeamData> teams = new ArrayList<>(teamManager.getTeams(context.getSource().getServer()));
		teams.sort(Comparator.comparing(TeamData::name));
		if (teams.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("No teams exist."), false);
			return Command.SINGLE_SUCCESS;
		}

		context.getSource().sendSuccess(() -> Component.literal("--- Teams ---").withStyle(ChatFormatting.YELLOW), false);
		for (TeamData team : teams) {
			String line = String.format(Locale.ROOT, "%s lvl=%d members=%d/%d bank=%d %s",
				team.name(),
				team.level(),
				team.memberIds().size(),
				team.maxMembers(),
				team.bankBalance(),
				CURRENCY_LABEL);
			context.getSource().sendSuccess(() -> Component.literal(line).withStyle(team.color()), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int showRankedTeams(CommandContext<CommandSourceStack> context) {
		List<TeamManager.TeamRankingEntry> rankings = teamManager.getRankedTeams(context.getSource().getServer());
		if (rankings.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("No teams exist."), false);
			return Command.SINGLE_SUCCESS;
		}

		context.getSource().sendSuccess(() -> Component.literal("--- Team Rankings ---").withStyle(ChatFormatting.YELLOW), false);
		for (int index = 0; index < rankings.size(); index++) {
			TeamManager.TeamRankingEntry ranking = rankings.get(index);
			TeamData team = ranking.team();
			String line = String.format(Locale.ROOT, "%d. %s lvl=%d members=%d/%d bank=%d %s score=%.1f%%",
				index + 1,
				team.name(),
				team.level(),
				team.memberIds().size(),
				team.maxMembers(),
				team.bankBalance(),
				CURRENCY_LABEL,
				ranking.score() * 100.0D);
			context.getSource().sendSuccess(() -> Component.literal(line).withStyle(team.color()), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int leaveTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TeamData team = requirePlayerTeam(context, player);
		if (team == null) {
			return 0;
		}

		if (teamManager.isLeader(team, player.getUUID())) {
			if (team.memberIds().size() > 1) {
				context.getSource().sendFailure(Component.literal("Transfer leadership before you can leave the team."));
				return 0;
			}

			teamManager.kickMember(context.getSource().getServer(), team, player.getUUID());
			context.getSource().sendSuccess(() -> Component.literal("You left and disbanded team ").append(teamManager.describeTeam(team)).append(Component.literal(".")), false);
			return Command.SINGLE_SUCCESS;
		}

		teamManager.kickMember(context.getSource().getServer(), team, player.getUUID());
		context.getSource().sendSuccess(() -> Component.literal("You left team ").append(teamManager.describeTeam(team)).append(Component.literal(".")), false);

		ServerPlayer leader = context.getSource().getServer().getPlayerList().getPlayer(team.leaderId());
		if (leader != null) {
			leader.sendSystemMessage(Component.literal(player.getName().getString() + " left your team ").append(teamManager.describeTeam(team)).append(Component.literal(".")));
		}

		return Command.SINGLE_SUCCESS;
	}

	private int teamChat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer sender = context.getSource().getPlayerOrException();
		TeamData team = requirePlayerTeam(context, sender);
		if (team == null) {
			return 0;
		}

		String message = StringArgumentType.getString(context, "message");
		MutableComponent chatMessage = Component.literal("[Team] ").withStyle(team.color(), ChatFormatting.BOLD)
			.append(Component.literal(sender.getName().getString()).withStyle(team.color()))
			.append(Component.literal(": " + message).withStyle(ChatFormatting.WHITE));

		for (UUID memberId : team.memberIds()) {
			ServerPlayer member = context.getSource().getServer().getPlayerList().getPlayer(memberId);
			if (member != null) {
				member.sendSystemMessage(chatMessage);
			}
		}

		return Command.SINGLE_SUCCESS;
	}

	private TeamData requireLeaderTeam(CommandContext<CommandSourceStack> context, ServerPlayer player) {
		TeamData team = requirePlayerTeam(context, player);
		if (team == null) {
			return null;
		}

		if (!teamManager.isLeader(team, player.getUUID())) {
			context.getSource().sendFailure(Component.literal("Only the team leader can use that command."));
			return null;
		}
		return team;
	}

	private MutableComponent buildInviteMessage(TeamData team, String inviterName) {
		return Component.literal(inviterName + " invited you to join ")
			.append(teamManager.describeTeam(team))
			.append(Component.literal(". "))
			.append(actionButton("Accept", ChatFormatting.GREEN, "/zv team accept " + team.name(), "Join this team"))
			.append(Component.literal(" "))
			.append(actionButton("Deny", ChatFormatting.RED, "/zv team deny " + team.name(), "Decline this invite"));
	}

	private MutableComponent actionButton(String label, ChatFormatting color, String command, String hoverText) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(color)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
	}

	private MutableComponent formatCurrency(int amount) {
		return Component.literal(amount + " " + CURRENCY_LABEL).withStyle(ChatFormatting.GOLD);
	}

	private ChatFormatting parseColor(String rawColor) {
		String normalized = rawColor.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		ChatFormatting color = ChatFormatting.getByName(normalized);
		if (color != null && color.isColor()) {
			return color;
		}

		// Support common color aliases (e.g. "dark red" -> DARK_RED)
		for (ChatFormatting candidate : ChatFormatting.values()) {
			if (!candidate.isColor()) continue;
			if (candidate.getName().equalsIgnoreCase(normalized)) {
				return candidate;
			}
		}

		return null;
	}

	private String listSupportedColors() {
		StringBuilder builder = new StringBuilder();
		for (ChatFormatting color : ChatFormatting.values()) {
			if (!color.isColor()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(color.getName());
		}
		return builder.toString();
	}
}