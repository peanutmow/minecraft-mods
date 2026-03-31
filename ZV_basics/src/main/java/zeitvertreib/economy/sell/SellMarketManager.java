package zeitvertreib.economy.sell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zeitvertreib.economy.ZeitvertreibEconomy;

public final class SellMarketManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Selling this many of an item halves its price. */
    private static final long HALF_LIFE = 500L;
    /** How often the decay step runs (5 real-time minutes). */
    private static final long DECAY_INTERVAL_MS = 5 * 60 * 1_000L;
    /** Volume counters shrink by 5% every decay interval. */
    private static final double DECAY_FACTOR = 0.95;

    private final Map<Identifier, Long> soldVolumes = new HashMap<>();
    private long lastDecayMillis = 0L;
    private boolean loaded = false;

    public void reset() {
        soldVolumes.clear();
        lastDecayMillis = 0L;
        loaded = false;
    }

    public void load(MinecraftServer server) {
        if (loaded) return;
        loaded = true;
        Path path = getFilePath(server);
        if (!Files.exists(path)) return;
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(raw);
            if (el.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                    Identifier id = Identifier.tryParse(e.getKey());
                    if (id != null) {
                        soldVolumes.put(id, Math.max(0L, e.getValue().getAsLong()));
                    }
                }
            }
        } catch (IOException ex) {
            ZeitvertreibEconomy.LOGGER.error("Failed to load sell market data from {}", path, ex);
        }
    }

    /** Returns true if the item has a configured base price. */
    public boolean isSellable(Item item) {
        return ItemSellPriceRegistry.BASE_PRICES.containsKey(item);
    }

    /**
     * Returns the current sell price for {@code item}, depressed by volume.
     * Formula: max(1, floor(base / (1 + soldVolume / HALF_LIFE)))
     */
    public int getCurrentPrice(Item item) {
        Integer base = ItemSellPriceRegistry.BASE_PRICES.get(item);
        if (base == null) return -1;
        long volume = soldVolumes.getOrDefault(BuiltInRegistries.ITEM.getKey(item), 0L);
        return Math.max(1, (int) Math.floor(base / (1.0 + (double) volume / HALF_LIFE)));
    }

    /** Returns the unmodified base price, or -1 if unsellable. */
    public int getBasePrice(Item item) {
        return ItemSellPriceRegistry.BASE_PRICES.getOrDefault(item, -1);
    }

    /** Records a sale of {@code quantity} of {@code item} and persists. */
    public void recordSale(MinecraftServer server, Item item, int quantity) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        soldVolumes.merge(id, (long) quantity, Long::sum);
        save(server);
    }

    /**
     * Called every server tick. Applies the decay step every {@link #DECAY_INTERVAL_MS}
     * milliseconds, recovering prices toward their base values.
     */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (lastDecayMillis == 0L) {
            lastDecayMillis = now;
            return;
        }
        if (now - lastDecayMillis < DECAY_INTERVAL_MS) return;
        lastDecayMillis = now;

        boolean changed = false;
        for (Map.Entry<Identifier, Long> entry : soldVolumes.entrySet()) {
            long decayed = (long) Math.floor(entry.getValue() * DECAY_FACTOR);
            if (decayed != entry.getValue()) {
                entry.setValue(decayed);
                changed = true;
            }
        }
        soldVolumes.entrySet().removeIf(e -> e.getValue() <= 0);
        if (changed) save(server);
    }

    /** Returns how many units of {@code item} have been sold since the last full reset. */
    public long getSoldVolume(Item item) {
        return soldVolumes.getOrDefault(BuiltInRegistries.ITEM.getKey(item), 0L);
    }

    /** Manually sets the sold volume for one item and persists. */
    public void setSoldVolume(MinecraftServer server, Item item, long volume) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (volume <= 0) {
            soldVolumes.remove(id);
        } else {
            soldVolumes.put(id, volume);
        }
        save(server);
    }

    /** Resets sold volume for a single item back to zero. */
    public void resetVolume(MinecraftServer server, Item item) {
        soldVolumes.remove(BuiltInRegistries.ITEM.getKey(item));
        save(server);
    }

    /** Clears all sold volumes. Returns the number of entries removed. */
    public int resetAllVolumes(MinecraftServer server) {
        int count = soldVolumes.size();
        soldVolumes.clear();
        save(server);
        return count;
    }

    /** Forces one decay step immediately, bypassing the normal interval timer. */
    public void forceDecay(MinecraftServer server) {
        boolean changed = false;
        for (Map.Entry<Identifier, Long> entry : soldVolumes.entrySet()) {
            long decayed = (long) Math.floor(entry.getValue() * DECAY_FACTOR);
            if (decayed != entry.getValue()) {
                entry.setValue(decayed);
                changed = true;
            }
        }
        soldVolumes.entrySet().removeIf(e -> e.getValue() <= 0);
        if (changed) save(server);
        lastDecayMillis = System.currentTimeMillis();
    }

    /** Returns all items that have a non-zero sold volume, sorted alphabetically by registry name. */
    public List<Item> getItemsWithVolume() {
        List<Item> result = new ArrayList<>();
        for (Identifier id : soldVolumes.keySet()) {
            if (soldVolumes.get(id) > 0 && BuiltInRegistries.ITEM.containsKey(id)) {
                result.add(BuiltInRegistries.ITEM.getValue(id));
            }
        }
        result.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return result;
    }

    private void save(MinecraftServer server) {
        Path path = getFilePath(server);
        JsonObject obj = new JsonObject();
        for (Map.Entry<Identifier, Long> e : soldVolumes.entrySet()) {
            obj.addProperty(e.getKey().toString(), e.getValue());
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ZeitvertreibEconomy.LOGGER.error("Failed to save sell market data to {}", path, ex);
        }
    }

    private Path getFilePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("zeitvertreib-economy-sell-market.json");
    }
}
