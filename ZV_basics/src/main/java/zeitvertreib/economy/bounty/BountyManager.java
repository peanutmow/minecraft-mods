package zeitvertreib.economy.bounty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import zeitvertreib.economy.ZeitvertreibEconomy;

public final class BountyManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<UUID, Integer> bounties = new HashMap<>();
	private boolean loaded;

	public void reset() {
		bounties.clear();
		loaded = false;
	}

	public int getBounty(MinecraftServer server, UUID targetId) {
		ensureLoaded(server);
		return bounties.getOrDefault(targetId, 0);
	}

	public void addBounty(MinecraftServer server, UUID targetId, int amount) {
		ensureLoaded(server);
		bounties.merge(targetId, amount, Integer::sum);
		save(server);
	}

	public int collectBounty(MinecraftServer server, UUID targetId) {
		ensureLoaded(server);
		int amount = bounties.getOrDefault(targetId, 0);
		if (amount > 0) {
			bounties.remove(targetId);
			save(server);
		}
		return amount;
	}

	public List<Map.Entry<UUID, Integer>> getTopBounties(MinecraftServer server, int limit) {
		ensureLoaded(server);
		return bounties.entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
			.limit(limit)
			.collect(Collectors.toList());
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
						bounties.put(playerId, Math.max(0, entry.getValue().getAsInt()));
					} catch (IllegalArgumentException e) {
						ZeitvertreibEconomy.LOGGER.warn("Skipping invalid bounty entry: {}", entry.getKey());
					}
				}
			}
		} catch (IOException e) {
			ZeitvertreibEconomy.LOGGER.error("Failed to load bounties", e);
		}
		loaded = true;
	}

	private void save(MinecraftServer server) {
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, Integer> entry : bounties.entrySet()) {
			if (entry.getValue() > 0) {
				root.addProperty(entry.getKey().toString(), entry.getValue());
			}
		}
		try {
			Path filePath = getFilePath(server);
			Files.createDirectories(filePath.getParent());
			Files.writeString(filePath, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ZeitvertreibEconomy.LOGGER.error("Failed to save bounties", e);
		}
	}

	private static Path getFilePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("zeitvertreib-economy-bounties.json");
	}
}
