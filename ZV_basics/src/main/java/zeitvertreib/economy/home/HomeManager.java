package zeitvertreib.economy.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import zeitvertreib.economy.ZeitvertreibEconomy;
import zeitvertreib.economy.team.TeamData;
import zeitvertreib.economy.team.TeamManager;

public final class HomeManager {
	public static final int BASE_MAX_HOMES = 1;
	public static final int LEVELS_PER_HOME = 5;
	public static final int SET_HOME_COST = 100;
	public static final long WARMUP_MILLIS = 3_000L;
	private static final double MOVE_THRESHOLD_SQ = 0.01;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final TeamManager teamManager;
	private final Map<UUID, Map<String, PlayerHome>> homes = new HashMap<>();
	private final Map<UUID, PendingHomeTeleport> pendingTeleports = new HashMap<>();
	private final Map<UUID, ServerBossEvent> bossEvents = new HashMap<>();
	private boolean loaded;

	public HomeManager(TeamManager teamManager) {
		this.teamManager = teamManager;
	}

	public void reset() {
		homes.clear();
		pendingTeleports.clear();
		bossEvents.values().forEach(ServerBossEvent::removeAllPlayers);
		bossEvents.clear();
		loaded = false;
	}

	public Map<String, PlayerHome> getHomes(MinecraftServer server, UUID playerId) {
		ensureLoaded(server);
		return Collections.unmodifiableMap(homes.getOrDefault(playerId, Collections.emptyMap()));
	}

	public PlayerHome getHome(MinecraftServer server, UUID playerId, String name) {
		ensureLoaded(server);
		Map<String, PlayerHome> playerHomes = homes.get(playerId);
		if (playerHomes == null) return null;
		return playerHomes.get(name.toLowerCase(Locale.ROOT));
	}

	public int getMaxHomes(MinecraftServer server, UUID playerId) {
		TeamData team = teamManager.getTeamForPlayer(server, playerId);
		int teamLevel = team != null ? team.level() : 0;
		return BASE_MAX_HOMES + teamLevel / LEVELS_PER_HOME;
	}

	public boolean setHome(MinecraftServer server, UUID playerId, String name, PlayerHome home) {
		ensureLoaded(server);
		Map<String, PlayerHome> playerHomes = homes.computeIfAbsent(playerId, k -> new LinkedHashMap<>());
		String key = name.toLowerCase(Locale.ROOT);
		if (!playerHomes.containsKey(key) && playerHomes.size() >= getMaxHomes(server, playerId)) {
			return false;
		}
		playerHomes.put(key, home);
		save(server);
		return true;
	}

	public boolean deleteHome(MinecraftServer server, UUID playerId, String name) {
		ensureLoaded(server);
		Map<String, PlayerHome> playerHomes = homes.get(playerId);
		if (playerHomes == null) return false;
		String key = name.toLowerCase(Locale.ROOT);
		if (playerHomes.remove(key) == null) return false;
		if (playerHomes.isEmpty()) homes.remove(playerId);
		save(server);
		return true;
	}

	public void registerPendingTeleport(MinecraftServer server, UUID playerId, PlayerHome home) {
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) return;

		PendingHomeTeleport pt = new PendingHomeTeleport();
		pt.playerId = playerId;
		pt.home = home;
		pt.originX = player.getX();
		pt.originY = player.getY();
		pt.originZ = player.getZ();
		pt.executeAtMillis = System.currentTimeMillis() + WARMUP_MILLIS;
		pendingTeleports.put(playerId, pt);

		ServerBossEvent boss = new ServerBossEvent(
			bossBarName(home.name(), 3),
			BossEvent.BossBarColor.GREEN,
			BossEvent.BossBarOverlay.PROGRESS
		);
		boss.setProgress(0.0f);
		boss.addPlayer(player);
		bossEvents.put(playerId, boss);
	}

	public boolean hasPendingTeleport(UUID playerId) {
		return pendingTeleports.containsKey(playerId);
	}

	public void cancelPendingTeleport(UUID playerId) {
		pendingTeleports.remove(playerId);
		removeBossEvent(playerId);
	}

	public void tickPendingTeleports(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (PendingHomeTeleport pt : new ArrayList<>(pendingTeleports.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(pt.playerId);
			if (player == null) {
				pendingTeleports.remove(pt.playerId);
				removeBossEvent(pt.playerId);
				continue;
			}

			ServerBossEvent boss = bossEvents.get(pt.playerId);

			if (pt.hasPlayerMoved(player.getX(), player.getY(), player.getZ())) {
				pt.reset(player.getX(), player.getY(), player.getZ());
				if (boss != null) {
					boss.setProgress(0.0f);
					boss.setName(bossBarName(pt.home.name(), 3));
				}
				player.displayClientMessage(
					Component.literal("Moved! Countdown reset — stand still.").withStyle(ChatFormatting.YELLOW), true);
				continue;
			}

			long remaining = pt.executeAtMillis - now;
			long elapsed = WARMUP_MILLIS - remaining;
			float progress = Math.min(1.0f, Math.max(0.0f, elapsed / (float) WARMUP_MILLIS));
			int secondsLeft = (int) Math.ceil(Math.max(0, remaining) / 1000.0);

			if (boss != null) {
				boss.setProgress(progress);
				boss.setName(bossBarName(pt.home.name(), secondsLeft));
			}
			player.displayClientMessage(
				Component.literal("Teleporting in ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
					.append(Component.literal("...").withStyle(ChatFormatting.GREEN)),
				true);

			if (now >= pt.executeAtMillis) {
				pendingTeleports.remove(pt.playerId);
				removeBossEvent(pt.playerId);

				Identifier dimId = Identifier.tryParse(pt.home.dimension());
				if (dimId == null) {
					player.sendSystemMessage(prefix().append(Component.literal("Invalid dimension for home.")));
					continue;
				}
				ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimId);
				ServerLevel level = server.getLevel(levelKey);
				if (level == null) {
					player.sendSystemMessage(prefix().append(Component.literal("Dimension not available.")));
					continue;
				}

				player.teleportTo(level, pt.home.x(), pt.home.y(), pt.home.z(),
					Set.of(), pt.home.yRot(), pt.home.xRot(), false);
				player.sendSystemMessage(prefix().append(
					Component.literal("Teleported to home '" + pt.home.name() + "'.")));
			}
		}
	}

	// --- Persistence ---

	private void ensureLoaded(MinecraftServer server) {
		if (loaded) return;
		Path filePath = getFilePath(server);
		if (!Files.exists(filePath)) {
			loaded = true;
			return;
		}
		try {
			String rawJson = Files.readString(filePath, StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(rawJson);
			if (jsonElement.isJsonObject()) {
				for (Map.Entry<String, JsonElement> playerEntry : jsonElement.getAsJsonObject().entrySet()) {
					UUID playerId;
					try {
						playerId = UUID.fromString(playerEntry.getKey());
					} catch (IllegalArgumentException e) {
						continue;
					}
					if (!playerEntry.getValue().isJsonObject()) continue;
					Map<String, PlayerHome> playerHomes = new LinkedHashMap<>();
					for (Map.Entry<String, JsonElement> homeEntry : playerEntry.getValue().getAsJsonObject().entrySet()) {
						if (!homeEntry.getValue().isJsonObject()) continue;
						JsonObject h = homeEntry.getValue().getAsJsonObject();
						playerHomes.put(homeEntry.getKey(), new PlayerHome(
							homeEntry.getKey(),
							h.has("dimension") ? h.get("dimension").getAsString() : "minecraft:overworld",
							h.has("x") ? h.get("x").getAsDouble() : 0,
							h.has("y") ? h.get("y").getAsDouble() : 0,
							h.has("z") ? h.get("z").getAsDouble() : 0,
							h.has("yRot") ? h.get("yRot").getAsFloat() : 0,
							h.has("xRot") ? h.get("xRot").getAsFloat() : 0
						));
					}
					homes.put(playerId, playerHomes);
				}
			}
		} catch (IOException e) {
			ZeitvertreibEconomy.LOGGER.error("Failed to load homes", e);
		}
		loaded = true;
	}

	private void save(MinecraftServer server) {
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, Map<String, PlayerHome>> playerEntry : homes.entrySet()) {
			JsonObject playerObj = new JsonObject();
			for (Map.Entry<String, PlayerHome> homeEntry : playerEntry.getValue().entrySet()) {
				JsonObject h = new JsonObject();
				PlayerHome home = homeEntry.getValue();
				h.addProperty("dimension", home.dimension());
				h.addProperty("x", home.x());
				h.addProperty("y", home.y());
				h.addProperty("z", home.z());
				h.addProperty("yRot", home.yRot());
				h.addProperty("xRot", home.xRot());
				playerObj.add(homeEntry.getKey(), h);
			}
			root.add(playerEntry.getKey().toString(), playerObj);
		}
		try {
			Path filePath = getFilePath(server);
			Files.createDirectories(filePath.getParent());
			Files.writeString(filePath, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ZeitvertreibEconomy.LOGGER.error("Failed to save homes", e);
		}
	}

	private static Path getFilePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("zeitvertreib-economy-homes.json");
	}

	private void removeBossEvent(UUID playerId) {
		ServerBossEvent boss = bossEvents.remove(playerId);
		if (boss != null) {
			boss.removeAllPlayers();
		}
	}

	private static MutableComponent bossBarName(String homeName, int secondsLeft) {
		return Component.literal("Teleporting to ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal(homeName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
			.append(Component.literal(" in ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	public static MutableComponent prefix() {
		return Component.literal("[Home] ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
	}

	private static class PendingHomeTeleport {
		UUID playerId;
		PlayerHome home;
		double originX, originY, originZ;
		long executeAtMillis;

		boolean hasPlayerMoved(double x, double y, double z) {
			double dx = x - originX;
			double dy = y - originY;
			double dz = z - originZ;
			return dx * dx + dy * dy + dz * dz > MOVE_THRESHOLD_SQ;
		}

		void reset(double x, double y, double z) {
			originX = x;
			originY = y;
			originZ = z;
			executeAtMillis = System.currentTimeMillis() + WARMUP_MILLIS;
		}
	}
}
