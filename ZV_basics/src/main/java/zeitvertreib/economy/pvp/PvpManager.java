package zeitvertreib.economy.pvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import zeitvertreib.economy.ZeitvertreibEconomy;

public final class PvpManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final long TOGGLE_COOLDOWN_MILLIS = 10L * 60L * 1000L;
	private static final long BLOCKED_MESSAGE_COOLDOWN_MILLIS = 3_000L;

	private final Map<UUID, Boolean> pvpEnabledByPlayer = new HashMap<>();
	private final Map<UUID, Long> lastToggleAtMillisByPlayer = new HashMap<>();
	private final Map<UUID, Long> lastBlockedMessageAtMillisByPlayer = new HashMap<>();
	private MinecraftServer activeServer;
	private boolean loaded;

	public void reset() {
		pvpEnabledByPlayer.clear();
		lastToggleAtMillisByPlayer.clear();
		lastBlockedMessageAtMillisByPlayer.clear();
		activeServer = null;
		loaded = false;
	}

	public void attachServer(MinecraftServer server) {
		activeServer = server;
		ensureLoaded(server);
	}

	public boolean isPvpEnabled(MinecraftServer server, UUID playerId) {
		ensureLoaded(server);
		return pvpEnabledByPlayer.getOrDefault(playerId, true);
	}

	public long getRemainingCooldownMillis(MinecraftServer server, UUID playerId) {
		ensureLoaded(server);
		long lastToggleAtMillis = lastToggleAtMillisByPlayer.getOrDefault(playerId, 0L);
		long remainingMillis = (lastToggleAtMillis + TOGGLE_COOLDOWN_MILLIS) - System.currentTimeMillis();
		return Math.max(0L, remainingMillis);
	}

	public long getLastToggleAtMillis(MinecraftServer server, UUID playerId) {
		ensureLoaded(server);
		return Math.max(0L, lastToggleAtMillisByPlayer.getOrDefault(playerId, 0L));
	}

	public ToggleResult setPvpEnabled(MinecraftServer server, UUID playerId, boolean enabled) {
		ensureLoaded(server);
		boolean currentState = isPvpEnabled(server, playerId);
		if (currentState == enabled) {
			return new ToggleResult(ToggleStatus.ALREADY_SET, currentState, 0L);
		}

		long remainingCooldownMillis = getRemainingCooldownMillis(server, playerId);
		if (remainingCooldownMillis > 0L) {
			return new ToggleResult(ToggleStatus.COOLDOWN_ACTIVE, currentState, remainingCooldownMillis);
		}

		pvpEnabledByPlayer.put(playerId, enabled);
		lastToggleAtMillisByPlayer.put(playerId, System.currentTimeMillis());
		save(server);
		return new ToggleResult(ToggleStatus.UPDATED, enabled, TOGGLE_COOLDOWN_MILLIS);
	}

	public boolean allowsPlayerCombat(MinecraftServer server, ServerPlayer attacker, ServerPlayer target) {
		if (server == null || attacker == null || target == null) {
			return true;
		}

		if (attacker.getUUID().equals(target.getUUID())) {
			return true;
		}

		return isPvpEnabled(server, attacker.getUUID()) && isPvpEnabled(server, target.getUUID());
	}

	public boolean allowsPlayerCombat(ServerPlayer attacker, ServerPlayer target) {
		return allowsPlayerCombat(activeServer, attacker, target);
	}

	public void notifyBlockedAttack(ServerPlayer attacker, ServerPlayer target) {
		if (attacker == null || target == null) {
			return;
		}

		long now = System.currentTimeMillis();
		long lastMessageAtMillis = lastBlockedMessageAtMillisByPlayer.getOrDefault(attacker.getUUID(), 0L);
		if ((now - lastMessageAtMillis) < BLOCKED_MESSAGE_COOLDOWN_MILLIS) {
			return;
		}

		lastBlockedMessageAtMillisByPlayer.put(attacker.getUUID(), now);
		attacker.sendSystemMessage(Component.literal(getCombatBlockMessage(attacker, target)));
	}

	public PlayerPvpState getPlayerState(MinecraftServer server, UUID playerId) {
		ensureLoaded(server);
		return new PlayerPvpState(
			isPvpEnabled(server, playerId),
			getLastToggleAtMillis(server, playerId),
			getRemainingCooldownMillis(server, playerId)
		);
	}

	public PlayerPvpState clearPlayerState(MinecraftServer server, UUID playerId) {
		ensureLoaded(server);
		PlayerPvpState previousState = getPlayerState(server, playerId);
		pvpEnabledByPlayer.remove(playerId);
		lastToggleAtMillisByPlayer.remove(playerId);
		lastBlockedMessageAtMillisByPlayer.remove(playerId);
		save(server);
		return previousState;
	}

	private String getCombatBlockMessage(ServerPlayer attacker, ServerPlayer target) {
		boolean attackerEnabled = isPvpEnabled(activeServer, attacker.getUUID());
		boolean targetEnabled = isPvpEnabled(activeServer, target.getUUID());
		if (!attackerEnabled && !targetEnabled) {
			return "PvP is disabled for both you and " + target.getName().getString() + ".";
		}
		if (!attackerEnabled) {
			return "You have PvP disabled. Use /zv pvp true to opt back in.";
		}
		return target.getName().getString() + " has PvP disabled.";
	}

	private void ensureLoaded(MinecraftServer server) {
		if (loaded) {
			return;
		}

		Path filePath = getFilePath(server);
		if (!Files.exists(filePath)) {
			loaded = true;
			return;
		}

		try {
			String rawJson = Files.readString(filePath, StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(rawJson);
			if (jsonElement.isJsonObject()) {
				JsonObject rootObject = jsonElement.getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : rootObject.entrySet()) {
					try {
						UUID playerId = UUID.fromString(entry.getKey());
						JsonObject playerObject = entry.getValue().getAsJsonObject();
						pvpEnabledByPlayer.put(playerId, !playerObject.has("pvpEnabled") || playerObject.get("pvpEnabled").getAsBoolean());
						if (playerObject.has("lastToggleAtMillis")) {
							lastToggleAtMillisByPlayer.put(playerId, Math.max(0L, playerObject.get("lastToggleAtMillis").getAsLong()));
						}
					} catch (IllegalArgumentException | IllegalStateException exception) {
						ZeitvertreibEconomy.LOGGER.warn("Skipping invalid PvP entry for {}", entry.getKey());
					}
				}
			}
		} catch (IOException exception) {
			ZeitvertreibEconomy.LOGGER.error("Failed to load PvP settings from {}", filePath, exception);
		}

		loaded = true;
	}

	private void save(MinecraftServer server) {
		Path filePath = getFilePath(server);
		JsonObject rootObject = new JsonObject();

		for (Map.Entry<UUID, Boolean> entry : pvpEnabledByPlayer.entrySet()) {
			JsonObject playerObject = new JsonObject();
			playerObject.addProperty("pvpEnabled", entry.getValue());
			playerObject.addProperty("lastToggleAtMillis", Math.max(0L, lastToggleAtMillisByPlayer.getOrDefault(entry.getKey(), 0L)));
			rootObject.add(entry.getKey().toString(), playerObject);
		}

		for (Map.Entry<UUID, Long> entry : lastToggleAtMillisByPlayer.entrySet()) {
			if (rootObject.has(entry.getKey().toString())) {
				continue;
			}

			JsonObject playerObject = new JsonObject();
			playerObject.addProperty("pvpEnabled", true);
			playerObject.addProperty("lastToggleAtMillis", Math.max(0L, entry.getValue()));
			rootObject.add(entry.getKey().toString(), playerObject);
		}

		try {
			Files.createDirectories(filePath.getParent());
			Files.writeString(filePath, GSON.toJson(rootObject), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			ZeitvertreibEconomy.LOGGER.error("Failed to save PvP settings to {}", filePath, exception);
		}
	}

	private Path getFilePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("zeitvertreib-economy-pvp.json");
	}

	public record ToggleResult(ToggleStatus status, boolean pvpEnabled, long remainingCooldownMillis) {
	}

	public record PlayerPvpState(boolean pvpEnabled, long lastToggleAtMillis, long remainingCooldownMillis) {
	}

	public enum ToggleStatus {
		UPDATED,
		ALREADY_SET,
		COOLDOWN_ACTIVE
	}
}