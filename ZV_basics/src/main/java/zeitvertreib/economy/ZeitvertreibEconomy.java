package zeitvertreib.economy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalTime;
import java.time.ZoneId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import zeitvertreib.economy.command.BountyCommands;
import zeitvertreib.economy.command.DevCommands;
import zeitvertreib.economy.network.StatsSync;
import zeitvertreib.economy.command.EconomyCommands;
import zeitvertreib.economy.command.HomeCommands;
import zeitvertreib.economy.command.SellCommands;
import zeitvertreib.economy.command.TeamCommands;
import zeitvertreib.economy.command.TpaCommands;
import zeitvertreib.economy.tpa.TpaManager;
import zeitvertreib.economy.sell.SellMarketManager;
import zeitvertreib.economy.bounty.BountyManager;
import zeitvertreib.economy.config.EconomyConfig;
import zeitvertreib.economy.currency.CurrencyManager;
import zeitvertreib.economy.daily.DailyBonusManager;
import zeitvertreib.economy.home.HomeManager;
import zeitvertreib.economy.loot.DungeonLootBookInjector;
import zeitvertreib.economy.loot.DiamondOreDropModifier;
import zeitvertreib.economy.loot.IronOreDropModifier;
import zeitvertreib.economy.mob.MobRewardRegistry;
import zeitvertreib.economy.pvp.PvpManager;
import zeitvertreib.economy.team.TeamManager;
import zeitvertreib.economy.trade.TradeOfferManager;

public class ZeitvertreibEconomy implements ModInitializer {
	public static final String MOD_ID = "zeitvertreib-economy";
	public static final EconomyConfig CONFIG = new EconomyConfig();
	public static final CurrencyManager CURRENCY_MANAGER = new CurrencyManager();
	public static final PvpManager PVP_MANAGER = new PvpManager();
	public static final TeamManager TEAM_MANAGER = new TeamManager();
	public static final TradeOfferManager TRADE_OFFER_MANAGER = new TradeOfferManager();
	public static final TpaManager TPA_MANAGER = new TpaManager();
	public static final SellMarketManager SELL_MARKET = new SellMarketManager();
	public static final HomeManager HOME_MANAGER = new HomeManager(TEAM_MANAGER);
	public static final BountyManager BOUNTY_MANAGER = new BountyManager();
	public static final DailyBonusManager DAILY_BONUS = new DailyBonusManager();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static int statsSyncTick = 0;

	@Override
	public void onInitialize() {
		StatsSync.register();
		EconomyCommands commands = new EconomyCommands(CONFIG, CURRENCY_MANAGER, PVP_MANAGER, TRADE_OFFER_MANAGER);
		DevCommands devCommands = new DevCommands(CONFIG, CURRENCY_MANAGER, PVP_MANAGER, TEAM_MANAGER, TRADE_OFFER_MANAGER, SELL_MARKET);
		TeamCommands teamCommands = new TeamCommands(CURRENCY_MANAGER, TEAM_MANAGER);
		TpaCommands tpaCommands = new TpaCommands(CURRENCY_MANAGER, TPA_MANAGER);
		SellCommands sellCommands = new SellCommands(CURRENCY_MANAGER, SELL_MARKET);
		HomeCommands homeCommands = new HomeCommands(CURRENCY_MANAGER, HOME_MANAGER);
		BountyCommands bountyCommands = new BountyCommands(CURRENCY_MANAGER, BOUNTY_MANAGER);

		DungeonLootBookInjector.register();
		DiamondOreDropModifier.register();
		IronOreDropModifier.register();

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!CONFIG.isRequireCopperForIron()) return true;
			if (player.isCreative()) return true;

			Block block = state.getBlock();
			if (block != Blocks.IRON_ORE && block != Blocks.DEEPSLATE_IRON_ORE) return true;

			Item held = player.getMainHandItem().getItem();
			if (held == Items.COPPER_PICKAXE || held == Items.IRON_PICKAXE
					|| held == Items.DIAMOND_PICKAXE || held == Items.NETHERITE_PICKAXE) {
				return true;
			}

			if (player instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.literal("You need a copper pickaxe or better to mine iron ore."));
			}
			return false;
		});

		CommandRegistrationCallback.EVENT.register(commands::register);
		CommandRegistrationCallback.EVENT.register(devCommands::register);
		CommandRegistrationCallback.EVENT.register(teamCommands::register);
		CommandRegistrationCallback.EVENT.register(tpaCommands::register);
		CommandRegistrationCallback.EVENT.register(sellCommands::register);
		CommandRegistrationCallback.EVENT.register(homeCommands::register);
		CommandRegistrationCallback.EVENT.register(bountyCommands::register);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, damageAmount) -> {
			if (!(entity instanceof net.minecraft.server.level.ServerPlayer targetPlayer)) {
				return true;
			}
			if (!(damageSource.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker)) {
				return true;
			}

			// Block damage between teammates
			net.minecraft.server.MinecraftServer srv = ((net.minecraft.server.level.ServerLevel) targetPlayer.level()).getServer();
			zeitvertreib.economy.team.TeamData attackerTeam = TEAM_MANAGER.getTeamForPlayer(srv, attacker.getUUID());
			zeitvertreib.economy.team.TeamData targetTeam = TEAM_MANAGER.getTeamForPlayer(srv, targetPlayer.getUUID());
			if (attackerTeam != null && targetTeam != null && attackerTeam.name().equals(targetTeam.name())) {
				attacker.displayClientMessage(Component.literal("You can't attack your own teammate!").withStyle(net.minecraft.ChatFormatting.RED), true);
				return false;
			}

			boolean allowsCombat = PVP_MANAGER.allowsPlayerCombat(attacker, targetPlayer);
			if (!allowsCombat) {
				PVP_MANAGER.notifyBlockedAttack(attacker, targetPlayer);
			}
			return allowsCombat;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			net.minecraft.server.MinecraftServer srv = ((net.minecraft.server.level.ServerLevel) entity.level()).getServer();

			// Mob kill rewards
			if (damageSource.getEntity() instanceof ServerPlayer killer) {
				Integer reward = MobRewardRegistry.REWARDS.get(entity.getType());
				if (reward != null) {
					CURRENCY_MANAGER.addBalance(srv, killer.getUUID(), reward);
					killer.displayClientMessage(
						Component.literal("+" + reward + " coins")
							.withStyle(net.minecraft.ChatFormatting.GOLD), true);
				}
			}

			// Player death handling
			if (entity instanceof ServerPlayer victim) {
				if (damageSource.getEntity() instanceof ServerPlayer killer && !killer.getUUID().equals(victim.getUUID())) {
					// PvP kill: killer gets 50%, victim loses 50%
					int bounty = BOUNTY_MANAGER.collectBounty(srv, victim.getUUID());
					if (bounty > 0) {
						CURRENCY_MANAGER.addBalance(srv, killer.getUUID(), bounty);
						killer.sendSystemMessage(
							Component.literal("[Bounty] ").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
								.append(Component.literal("You collected the bounty on " + victim.getName().getString() + "! "))
								.append(Component.literal("+" + bounty + " coins").withStyle(net.minecraft.ChatFormatting.GOLD))
								.append(Component.literal(".")));
						victim.sendSystemMessage(
							Component.literal("[Bounty] ").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
								.append(Component.literal(killer.getName().getString() + " collected the bounty on you.")));
					}

					int victimBalance = CURRENCY_MANAGER.getBalance(srv, victim.getUUID());
					int stolenAmount = victimBalance / 2;
					if (stolenAmount > 0) {
						CURRENCY_MANAGER.addBalance(srv, killer.getUUID(), stolenAmount);
						int newVictimBalance = victimBalance - stolenAmount;
						CURRENCY_MANAGER.setBalance(srv, victim.getUUID(), newVictimBalance);

						killer.sendSystemMessage(
							Component.literal("[PvP] ").withStyle(net.minecraft.ChatFormatting.AQUA)
								.append(Component.literal("You stole 50% of ").withStyle(net.minecraft.ChatFormatting.WHITE))
								.append(Component.literal(victim.getName().getString()).withStyle(net.minecraft.ChatFormatting.WHITE))
								.append(Component.literal("'s money! ").withStyle(net.minecraft.ChatFormatting.WHITE))
								.append(Component.literal("+" + stolenAmount + " coins").withStyle(net.minecraft.ChatFormatting.GOLD))
								.append(Component.literal(".").withStyle(net.minecraft.ChatFormatting.WHITE)));
						victim.sendSystemMessage(
							Component.literal("[PvP] ").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
								.append(Component.literal(killer.getName().getString()).withStyle(net.minecraft.ChatFormatting.WHITE))
								.append(Component.literal(" stole 50% of your money! ").withStyle(net.minecraft.ChatFormatting.WHITE))
								.append(Component.literal("-" + stolenAmount + " coins").withStyle(net.minecraft.ChatFormatting.RED))
								.append(Component.literal(".").withStyle(net.minecraft.ChatFormatting.WHITE)));
					}
				} else {
					// Environmental death: victim loses 20% of coins
					int victimBalance = CURRENCY_MANAGER.getBalance(srv, victim.getUUID());
					int lostAmount = victimBalance / 5;
					if (lostAmount > 0) {
						int newBalance = victimBalance - lostAmount;
						CURRENCY_MANAGER.setBalance(srv, victim.getUUID(), newBalance);

						victim.sendSystemMessage(
							Component.literal("[Death] ").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
								.append(Component.literal("You lost 20% of your coins! "))
								.append(Component.literal("-" + lostAmount + " coins").withStyle(net.minecraft.ChatFormatting.RED))
								.append(Component.literal(".")));
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TRADE_OFFER_MANAGER::cleanupExpiredOffers);
		ServerTickEvents.END_SERVER_TICK.register(TEAM_MANAGER::cleanupExpiredInvites);
		ServerTickEvents.END_SERVER_TICK.register(TPA_MANAGER::cleanupExpiredRequests);
		ServerTickEvents.END_SERVER_TICK.register(server -> TPA_MANAGER.tickPendingTeleports(server, CURRENCY_MANAGER));
		ServerTickEvents.END_SERVER_TICK.register(SELL_MARKET::tick);
		ServerTickEvents.END_SERVER_TICK.register(HOME_MANAGER::tickPendingTeleports);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++statsSyncTick >= 40) {
				statsSyncTick = 0;
				StatsSync.sendToAll(server, CURRENCY_MANAGER, TEAM_MANAGER);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			
			if (server.isDedicatedServer()) {
				// Check if player is an OP by checking their UUID against the ops list
				boolean isOp = false;
				try {
					java.nio.file.Path opsFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).getParent().resolve("ops.json");
					if (java.nio.file.Files.exists(opsFile)) {
						String opsJson = new String(java.nio.file.Files.readAllBytes(opsFile));
						if (opsJson.contains("\"" + player.getUUID().toString() + "\"")) {
							isOp = true;
						}
					}
				} catch (Exception e) {
					// If we can't read ops.json, allow the join to have a safe default
				}
				
				// Only allow joins between 16:00 and 21:00 Berlin time (OPs bypass this check)
				if (!isOp) {
					LocalTime berlinTime = LocalTime.now(ZoneId.of("Europe/Berlin"));
					boolean isOpen = berlinTime.isAfter(LocalTime.of(15, 59)) && berlinTime.isBefore(LocalTime.of(21, 0));
					if (!isOpen) {
						server.execute(() -> player.connection.disconnect(Component.literal("Der Server ist nur zwischen 16:00 und 21:00 Uhr geöffnet. Bitte versuche es später erneut!").withStyle(net.minecraft.ChatFormatting.RED)));
						return;
					}
				}
			}
			
			TEAM_MANAGER.syncDisplays(server);
			TEAM_MANAGER.syncDisplayForPlayer(server, player);
			DAILY_BONUS.tryClaimBonus(server, player, CURRENCY_MANAGER);
			StatsSync.sendToPlayer(server, player, CURRENCY_MANAGER, TEAM_MANAGER);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TRADE_OFFER_MANAGER.cancelOffersForPlayer(server, handler.getPlayer());
			TEAM_MANAGER.clearDisplayForPlayer(server, handler.getPlayer());
			TPA_MANAGER.cancelPendingTeleportForPlayer(server, handler.getPlayer().getUUID(), CURRENCY_MANAGER);
			HOME_MANAGER.cancelPendingTeleport(handler.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			CONFIG.load();
			CURRENCY_MANAGER.reset();
			PVP_MANAGER.reset();
			PVP_MANAGER.attachServer(server);
			TEAM_MANAGER.load(server);
			TRADE_OFFER_MANAGER.reset();
			TPA_MANAGER.reset();
			SELL_MARKET.reset();
			SELL_MARKET.load(server);
			HOME_MANAGER.reset();
			BOUNTY_MANAGER.reset();
			DAILY_BONUS.reset();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(TEAM_MANAGER::syncDisplays);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRADE_OFFER_MANAGER.cancelAll(server);
			CURRENCY_MANAGER.reset();
			PVP_MANAGER.reset();
			TEAM_MANAGER.reset();
			TRADE_OFFER_MANAGER.reset();
			SELL_MARKET.reset();
			HOME_MANAGER.reset();
			BOUNTY_MANAGER.reset();
			DAILY_BONUS.reset();
		});

		LOGGER.info("Zeitvertreib Economy initialized");
	}
}