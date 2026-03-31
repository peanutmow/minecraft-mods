package zeitvertreib.economy.tpa;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import zeitvertreib.economy.ZeitvertreibEconomy;
import zeitvertreib.economy.currency.CurrencyManager;

public final class TpaManager {
	public static final long TELEPORT_COOLDOWN_MILLIS = 5L * 60L * 1000L;

	// One outgoing request per sender, one incoming per target.
	// Sending a new request cancels any prior outgoing/overwritten incoming.
	private final Map<UUID, TpaRequest> pendingBySender = new HashMap<>();
	private final Map<UUID, TpaRequest> pendingByTarget = new HashMap<>();
	private final Map<UUID, Long> lastTeleportAtMillis = new HashMap<>();
	private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
	private final Map<UUID, ServerBossEvent> bossEvents = new HashMap<>();

	public void reset() {
		pendingBySender.clear();
		pendingByTarget.clear();
		lastTeleportAtMillis.clear();
		pendingTeleports.clear();
		bossEvents.values().forEach(ServerBossEvent::removeAllPlayers);
		bossEvents.clear();
	}

	public void sendRequest(UUID requesterId, UUID targetId, String requesterName, String targetName, TpaRequest.TpaType type) {
		// Cancel any previous outgoing request from this sender.
		TpaRequest existingOutgoing = pendingBySender.get(requesterId);
		if (existingOutgoing != null) {
			pendingByTarget.remove(existingOutgoing.targetId());
		}

		// Cancel any previous incoming request for the target (only one pending per target).
		TpaRequest existingIncoming = pendingByTarget.get(targetId);
		if (existingIncoming != null) {
			pendingBySender.remove(existingIncoming.requesterId());
		}

		TpaRequest request = new TpaRequest(
			requesterId, targetId, requesterName, targetName, type,
			System.currentTimeMillis() + TpaRequest.REQUEST_TIMEOUT_MILLIS
		);
		pendingBySender.put(requesterId, request);
		pendingByTarget.put(targetId, request);
	}

	/**
	 * Returns the pending incoming request for the given player, or null if none / expired.
	 */
	public TpaRequest getIncomingRequest(UUID targetId) {
		TpaRequest req = pendingByTarget.get(targetId);
		if (req == null) {
			return null;
		}
		if (req.expiresAtMillis() <= System.currentTimeMillis()) {
			removeRequest(req);
			return null;
		}
		return req;
	}

	public void removeRequest(TpaRequest request) {
		pendingBySender.remove(request.requesterId());
		pendingByTarget.remove(request.targetId());
	}

	/**
	 * Returns remaining cooldown millis for the given player (0 if none).
	 * The cooldown is tracked for the requester, applied after a successful teleport.
	 */
	public long getRemainingCooldownMillis(UUID playerId) {
		long last = lastTeleportAtMillis.getOrDefault(playerId, 0L);
		return Math.max(0L, (last + TELEPORT_COOLDOWN_MILLIS) - System.currentTimeMillis());
	}

	public boolean hasPendingTeleport(UUID teleportingPlayerId) {
		return pendingTeleports.containsKey(teleportingPlayerId);
	}

	/**
	 * Registers a warmup teleport and shows the boss bar to the teleporting player.
	 */
	public void registerPendingTeleport(MinecraftServer server, PendingTeleport pt) {
		pendingTeleports.put(pt.teleportingPlayerId(), pt);

		ServerBossEvent boss = new ServerBossEvent(
			bossBarName(pt.destinationPlayerName(), 3),
			BossEvent.BossBarColor.BLUE,
			BossEvent.BossBarOverlay.PROGRESS
		);
		boss.setProgress(0.0f);
		ServerPlayer teleporting = server.getPlayerList().getPlayer(pt.teleportingPlayerId());
		if (teleporting != null) {
			boss.addPlayer(teleporting);
		}
		bossEvents.put(pt.teleportingPlayerId(), boss);
	}

	/**
	 * Cancels and refunds any pending teleport involving the given player (either as the teleporter or destination).
	 * Called on disconnect.
	 */
	public void cancelPendingTeleportForPlayer(MinecraftServer server, UUID playerId, CurrencyManager currencyManager) {
		for (PendingTeleport pt : new ArrayList<>(pendingTeleports.values())) {
			if (!pt.teleportingPlayerId().equals(playerId) && !pt.destinationPlayerId().equals(playerId)) {
				continue;
			}
			removeBossEvent(pt.teleportingPlayerId());
			pendingTeleports.remove(pt.teleportingPlayerId());
			currencyManager.addBalance(server, pt.requesterId(), pt.cost());

			ServerPlayer teleporting = server.getPlayerList().getPlayer(pt.teleportingPlayerId());
			if (teleporting != null) {
				teleporting.sendSystemMessage(prefix().append(
					Component.literal("Teleport cancelled because " + pt.destinationPlayerName() + " went offline.")));
			}
			ServerPlayer requester = server.getPlayerList().getPlayer(pt.requesterId());
			if (requester != null && !pt.requesterId().equals(pt.teleportingPlayerId())) {
				requester.sendSystemMessage(prefix().append(
					Component.literal("Teleport cancelled, your coins were refunded.")));
			}
		}
	}

	/**
	 * Called every server tick. Updates boss bars, resets on movement, executes or cancels pending teleports.
	 */
	public void tickPendingTeleports(MinecraftServer server, CurrencyManager currencyManager) {
		long now = System.currentTimeMillis();
		for (PendingTeleport pt : new ArrayList<>(pendingTeleports.values())) {
			ServerPlayer teleporting = server.getPlayerList().getPlayer(pt.teleportingPlayerId());
			ServerPlayer destination = server.getPlayerList().getPlayer(pt.destinationPlayerId());

			// Cancel if either player went offline — refund coins.
			if (teleporting == null || destination == null) {
				removeBossEvent(pt.teleportingPlayerId());
				pendingTeleports.remove(pt.teleportingPlayerId());
				currencyManager.addBalance(server, pt.requesterId(), pt.cost());
				String missingName = teleporting == null ? pt.teleportingPlayerName() : pt.destinationPlayerName();
				if (teleporting != null) {
					teleporting.sendSystemMessage(prefix().append(
						Component.literal("Teleport cancelled because " + missingName + " went offline.")));
				}
				ServerPlayer requester = server.getPlayerList().getPlayer(pt.requesterId());
				if (requester != null && !pt.requesterId().equals(pt.teleportingPlayerId())) {
					requester.sendSystemMessage(prefix().append(
						Component.literal("Teleport cancelled, your coins were refunded.")));
				}
				continue;
			}

			ServerBossEvent boss = bossEvents.get(pt.teleportingPlayerId());

			// Reset countdown if the teleporting player moved.
			if (pt.hasPlayerMoved(teleporting.getX(), teleporting.getY(), teleporting.getZ())) {
				pt.reset(teleporting.getX(), teleporting.getY(), teleporting.getZ());
				if (boss != null) {
					boss.setProgress(0.0f);
					boss.setName(bossBarName(pt.destinationPlayerName(), 3));
				}
				teleporting.displayClientMessage(
					Component.literal("Moved! Countdown reset — stand still.").withStyle(ChatFormatting.YELLOW), true);
				continue;
			}

			// Update boss bar progress and send action bar countdown.
			long remaining = pt.executeAtMillis() - now;
			long elapsed = PendingTeleport.WARMUP_MILLIS - remaining;
			float progress = Math.min(1.0f, Math.max(0.0f, elapsed / (float) PendingTeleport.WARMUP_MILLIS));
			int secondsLeft = (int) Math.ceil(Math.max(0, remaining) / 1000.0);

			if (boss != null) {
				boss.setProgress(progress);
				boss.setName(bossBarName(pt.destinationPlayerName(), secondsLeft));
			}
			teleporting.displayClientMessage(
				Component.literal("Teleporting in ").withStyle(ChatFormatting.AQUA)
					.append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
					.append(Component.literal("...").withStyle(ChatFormatting.AQUA)),
				true);

			// Execute teleport when warmup has elapsed.
			if (now >= pt.executeAtMillis()) {
				removeBossEvent(pt.teleportingPlayerId());
				pendingTeleports.remove(pt.teleportingPlayerId());
				teleporting.teleportTo(
					(ServerLevel) destination.level(),
					destination.getX(),
					destination.getY(),
					destination.getZ(),
					Set.of(),
					destination.getYRot(),
					destination.getXRot(),
					false
				);
				recordTeleport(pt.requesterId());
				teleporting.sendSystemMessage(prefix().append(
					Component.literal("Teleported to " + pt.destinationPlayerName() + ".")));
			}
		}
	}

	public void recordTeleport(UUID playerId) {
		lastTeleportAtMillis.put(playerId, System.currentTimeMillis());
	}

	/**
	 * Computes TPA cost as 1% of the total coins held by all known players, minimum 1.
	 */
	public int computeCost(MinecraftServer server, CurrencyManager currencyManager) {
		int total = currencyManager.getSortedBalances(server).stream()
			.mapToInt(Map.Entry::getValue)
			.sum();
		return Math.min(1000, Math.max(1, total / 100));
	}

	public void cleanupExpiredRequests(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (TpaRequest req : new ArrayList<>(pendingBySender.values())) {
			if (req.expiresAtMillis() > now) {
				continue;
			}

			removeRequest(req);
			ZeitvertreibEconomy.LOGGER.debug("TPA request from {} to {} expired.", req.requesterName(), req.targetName());

			ServerPlayer requester = server.getPlayerList().getPlayer(req.requesterId());
			if (requester != null) {
				requester.sendSystemMessage(prefix().append(
					Component.literal("Your TPA request to " + req.targetName() + " expired.")));
			}

			ServerPlayer target = server.getPlayerList().getPlayer(req.targetId());
			if (target != null) {
				target.sendSystemMessage(prefix().append(
					Component.literal("The TPA request from " + req.requesterName() + " expired.")));
			}
		}
	}

	private void removeBossEvent(UUID teleportingPlayerId) {
		ServerBossEvent boss = bossEvents.remove(teleportingPlayerId);
		if (boss != null) {
			boss.removeAllPlayers();
		}
	}

	private static MutableComponent bossBarName(String destinationName, int secondsLeft) {
		return Component.literal("Teleporting to ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(destinationName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
			.append(Component.literal(" in ").withStyle(ChatFormatting.AQUA))
			.append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	private static MutableComponent prefix() {
		return Component.literal("[TPA] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	}
}
