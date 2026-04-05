package zeitvertreib.economy.daily;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import zeitvertreib.economy.currency.CurrencyManager;

public final class DailyBonusManager {
	public static final int BASE_BONUS = 10;
	public static final int STREAK_BONUS = 5;
	public static final int MAX_STREAK = 10;
	private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
	private static final long STREAK_RESET_MILLIS = 48L * 60L * 60L * 1000L;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<UUID, DailyClaim> claims = new HashMap<>();
	private boolean loaded;

	public void reset() {
		claims.clear();
		loaded = false;
	}

	public void tryClaimBonus(MinecraftServer server, ServerPlayer player, CurrencyManager currencyManager) {
		ensureLoaded(server);
		UUID playerId = player.getUUID();
		long now = System.currentTimeMillis();

		DailyClaim claim = claims.get(playerId);

		if (claim != null && (now - claim.lastClaimMillis) < DAY_MILLIS) {
			return;
		}

		int streak;
		if (claim == null) {
			streak = 1;
		} else if ((now - claim.lastClaimMillis) > STREAK_RESET_MILLIS) {
			streak = 1;
		} else {
			streak = claim.streak + 1;
		}

		int effectiveStreak = Math.min(streak, MAX_STREAK);
		int bonus = BASE_BONUS + (effectiveStreak - 1) * STREAK_BONUS;

		currencyManager.addBalance(server, playerId, bonus);
		claims.put(playerId, new DailyClaim(now, streak));
		save(server);

		player.sendSystemMessage(prefix()
			.append(Component.literal("Daily bonus! You received "))
			.append(formatCurrency(bonus))
			.append(Component.literal(".")));

		if (streak > 1) {
			player.sendSystemMessage(prefix()
				.append(Component.literal("Login streak: "))
				.append(Component.literal(streak + " days").withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(streak >= MAX_STREAK ? " (max bonus!)" : ".")));
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
			JsonElement el = JsonParser.parseString(rawJson);
			if (el.isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet()) {
					try {
						UUID playerId = UUID.fromString(entry.getKey());
						JsonObject obj = entry.getValue().getAsJsonObject();
						long lastClaim = obj.has("lastClaimMillis") ? obj.get("lastClaimMillis").getAsLong() : 0;
						int streak = obj.has("streak") ? obj.get("streak").getAsInt() : 0;
						claims.put(playerId, new DailyClaim(lastClaim, streak));
					} catch (Exception e) {
						ZeitvertreibEconomy.LOGGER.warn("Skipping invalid daily claim entry: {}", entry.getKey());
					}
				}
			}
		} catch (IOException e) {
			ZeitvertreibEconomy.LOGGER.error("Failed to load daily claims", e);
		}
		loaded = true;
	}

	private void save(MinecraftServer server) {
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, DailyClaim> entry : claims.entrySet()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("lastClaimMillis", entry.getValue().lastClaimMillis);
			obj.addProperty("streak", entry.getValue().streak);
			root.add(entry.getKey().toString(), obj);
		}
		try {
			Path filePath = getFilePath(server);
			Files.createDirectories(filePath.getParent());
			Files.writeString(filePath, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ZeitvertreibEconomy.LOGGER.error("Failed to save daily claims", e);
		}
	}

	private static Path getFilePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("zeitvertreib-economy-daily.json");
	}

	private static MutableComponent prefix() {
		return Component.literal("[Daily] ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
	}

	private static MutableComponent formatCurrency(int amount) {
		return Component.literal(amount + " coins").withStyle(ChatFormatting.GOLD);
	}

	private static class DailyClaim {
		final long lastClaimMillis;
		final int streak;

		DailyClaim(long lastClaimMillis, int streak) {
			this.lastClaimMillis = lastClaimMillis;
			this.streak = streak;
		}
	}
}
