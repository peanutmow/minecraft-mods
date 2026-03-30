package zeitvertreib.economy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import zeitvertreib.economy.ZeitvertreibEconomy;

public final class EconomyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final double DEFAULT_TRADE_TAX_PERCENT = 5.0D;
	private static final int DEFAULT_ANVIL_XP_COST = 5;
	private static final boolean DEFAULT_BLOCK_LIBRARIAN_BOOK_TRADES = true;
	private static final int DEFAULT_MAX_PLAYER_EXPERIENCE_LEVEL = 30;
	private static final float DEFAULT_DUNGEON_BOOK_CHANCE = 0.25F;
	private static final float DEFAULT_DIAMOND_DROP_CHANCE = 0.20F;
	private static final float DEFAULT_WITCH_BOOK_CHANCE = 0.10F;
	private static final float DEFAULT_EVOKER_BOOK_CHANCE = 0.50F;
	private static final boolean DEFAULT_IRON_ENABLED = true;
	private static final boolean DEFAULT_NETHER_PORTALS_ENABLED = true;
	private static final boolean DEFAULT_END_ENABLED = true;

	private double tradeTaxPercent = DEFAULT_TRADE_TAX_PERCENT;
	private int anvilXpCost = DEFAULT_ANVIL_XP_COST;
	private boolean blockLibrarianBookTrades = DEFAULT_BLOCK_LIBRARIAN_BOOK_TRADES;
	private int maxPlayerExperienceLevel = DEFAULT_MAX_PLAYER_EXPERIENCE_LEVEL;
	private float dungeonBookChance = DEFAULT_DUNGEON_BOOK_CHANCE;
	private float diamondDropChance = DEFAULT_DIAMOND_DROP_CHANCE;
	private float witchEnchantedBookChance = DEFAULT_WITCH_BOOK_CHANCE;
	private float evokerEnchantedBookChance = DEFAULT_EVOKER_BOOK_CHANCE;
	private boolean ironEnabled = DEFAULT_IRON_ENABLED;
	private boolean netherPortalsEnabled = DEFAULT_NETHER_PORTALS_ENABLED;
	private boolean endEnabled = DEFAULT_END_ENABLED;

	public void load() {
		Path configPath = getConfigPath();

		if (!Files.exists(configPath)) {
			tradeTaxPercent = DEFAULT_TRADE_TAX_PERCENT;
			save(configPath);
			return;
		}

		try {
			String rawJson = Files.readString(configPath, StandardCharsets.UTF_8);
			JsonObject jsonObject = JsonParser.parseString(rawJson).getAsJsonObject();
			tradeTaxPercent = jsonObject.has("tradeTaxPercent") ? jsonObject.get("tradeTaxPercent").getAsDouble() : DEFAULT_TRADE_TAX_PERCENT;
			anvilXpCost = jsonObject.has("anvilXpCost") ? jsonObject.get("anvilXpCost").getAsInt() : DEFAULT_ANVIL_XP_COST;
			blockLibrarianBookTrades = !jsonObject.has("blockLibrarianBookTrades") || jsonObject.get("blockLibrarianBookTrades").getAsBoolean();
			maxPlayerExperienceLevel = jsonObject.has("maxPlayerExperienceLevel") ? jsonObject.get("maxPlayerExperienceLevel").getAsInt() : DEFAULT_MAX_PLAYER_EXPERIENCE_LEVEL;
			dungeonBookChance = jsonObject.has("dungeonBookChance") ? jsonObject.get("dungeonBookChance").getAsFloat() : DEFAULT_DUNGEON_BOOK_CHANCE;
			diamondDropChance = jsonObject.has("diamondDropChance") ? jsonObject.get("diamondDropChance").getAsFloat() : DEFAULT_DIAMOND_DROP_CHANCE;
			witchEnchantedBookChance = jsonObject.has("witchEnchantedBookChance") ? jsonObject.get("witchEnchantedBookChance").getAsFloat() : DEFAULT_WITCH_BOOK_CHANCE;
			evokerEnchantedBookChance = jsonObject.has("evokerEnchantedBookChance") ? jsonObject.get("evokerEnchantedBookChance").getAsFloat() : DEFAULT_EVOKER_BOOK_CHANCE;
			ironEnabled = !jsonObject.has("ironEnabled") || jsonObject.get("ironEnabled").getAsBoolean();
			netherPortalsEnabled = !jsonObject.has("netherPortalsEnabled") || jsonObject.get("netherPortalsEnabled").getAsBoolean();
			endEnabled = !jsonObject.has("endEnabled") || jsonObject.get("endEnabled").getAsBoolean();
			sanitize();
			save(configPath);
		} catch (Exception exception) {
			tradeTaxPercent = DEFAULT_TRADE_TAX_PERCENT;
			anvilXpCost = DEFAULT_ANVIL_XP_COST;
			blockLibrarianBookTrades = DEFAULT_BLOCK_LIBRARIAN_BOOK_TRADES;
			maxPlayerExperienceLevel = DEFAULT_MAX_PLAYER_EXPERIENCE_LEVEL;
			dungeonBookChance = DEFAULT_DUNGEON_BOOK_CHANCE;
			diamondDropChance = DEFAULT_DIAMOND_DROP_CHANCE;
			witchEnchantedBookChance = DEFAULT_WITCH_BOOK_CHANCE;
			evokerEnchantedBookChance = DEFAULT_EVOKER_BOOK_CHANCE;
			ironEnabled = DEFAULT_IRON_ENABLED;
			netherPortalsEnabled = DEFAULT_NETHER_PORTALS_ENABLED;
			endEnabled = DEFAULT_END_ENABLED;
			ZeitvertreibEconomy.LOGGER.error("Failed to load config from {}. Falling back to defaults.", configPath, exception);
			save(configPath);
		}
	}

	public double getTradeTaxPercent() {
		return tradeTaxPercent;
	}

	public int getAnvilXpCost() {
		return anvilXpCost;
	}

	public boolean isBlockLibrarianBookTrades() {
		return blockLibrarianBookTrades;
	}

	public int getMaxPlayerExperienceLevel() {
		return maxPlayerExperienceLevel;
	}

	public float getDungeonBookChance() {
		return dungeonBookChance;
	}

	public float getDiamondDropChance() {
		return diamondDropChance;
	}

	public float getWitchEnchantedBookChance() {
		return witchEnchantedBookChance;
	}

	public float getEvokerEnchantedBookChance() {
		return evokerEnchantedBookChance;
	}

	public boolean isIronEnabled() {
		return ironEnabled;
	}

	public boolean isNetherPortalsEnabled() {
		return netherPortalsEnabled;
	}

	public boolean isEndEnabled() {
		return endEnabled;
	}

	public int calculateTax(int grossPrice) {
		if (grossPrice <= 0) {
			return 0;
		}

		int taxAmount = (int) Math.round(grossPrice * (tradeTaxPercent / 100.0D));
		return Math.max(0, Math.min(grossPrice, taxAmount));
	}

	public int calculateSellerProceeds(int grossPrice) {
		return Math.max(0, grossPrice - calculateTax(grossPrice));
	}

	public Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("zeitvertreib-economy.json");
	}

	private void sanitize() {
		if (Double.isNaN(tradeTaxPercent) || Double.isInfinite(tradeTaxPercent)) {
			tradeTaxPercent = DEFAULT_TRADE_TAX_PERCENT;
		}
		tradeTaxPercent = Math.max(0.0D, Math.min(100.0D, tradeTaxPercent));
		anvilXpCost = Math.max(1, anvilXpCost);
		maxPlayerExperienceLevel = Math.max(1, maxPlayerExperienceLevel);

		if (Float.isNaN(dungeonBookChance) || Float.isInfinite(dungeonBookChance)) {
			dungeonBookChance = DEFAULT_DUNGEON_BOOK_CHANCE;
		}
		dungeonBookChance = Math.max(0.0F, Math.min(1.0F, dungeonBookChance));

		if (Float.isNaN(diamondDropChance) || Float.isInfinite(diamondDropChance)) {
			diamondDropChance = DEFAULT_DIAMOND_DROP_CHANCE;
		}
		diamondDropChance = Math.max(0.0F, Math.min(1.0F, diamondDropChance));

		if (Float.isNaN(witchEnchantedBookChance) || Float.isInfinite(witchEnchantedBookChance)) {
			witchEnchantedBookChance = DEFAULT_WITCH_BOOK_CHANCE;
		}
		witchEnchantedBookChance = Math.max(0.0F, Math.min(1.0F, witchEnchantedBookChance));

		if (Float.isNaN(evokerEnchantedBookChance) || Float.isInfinite(evokerEnchantedBookChance)) {
			evokerEnchantedBookChance = DEFAULT_EVOKER_BOOK_CHANCE;
		}
		evokerEnchantedBookChance = Math.max(0.0F, Math.min(1.0F, evokerEnchantedBookChance));
	}

	private void save(Path configPath) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("tradeTaxPercent", tradeTaxPercent);
		jsonObject.addProperty("anvilXpCost", anvilXpCost);
		jsonObject.addProperty("blockLibrarianBookTrades", blockLibrarianBookTrades);
		jsonObject.addProperty("maxPlayerExperienceLevel", maxPlayerExperienceLevel);
		jsonObject.addProperty("dungeonBookChance", dungeonBookChance);
		jsonObject.addProperty("diamondDropChance", diamondDropChance);
		jsonObject.addProperty("witchEnchantedBookChance", witchEnchantedBookChance);
		jsonObject.addProperty("evokerEnchantedBookChance", evokerEnchantedBookChance);
		jsonObject.addProperty("ironEnabled", ironEnabled);
		jsonObject.addProperty("netherPortalsEnabled", netherPortalsEnabled);
		jsonObject.addProperty("endEnabled", endEnabled);

		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, GSON.toJson(jsonObject), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			ZeitvertreibEconomy.LOGGER.error("Failed to save config to {}", configPath, exception);
		}
	}
}