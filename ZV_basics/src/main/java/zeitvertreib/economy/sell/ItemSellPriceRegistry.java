package zeitvertreib.economy.sell;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class ItemSellPriceRegistry {

    public static final Map<Item, Integer> BASE_PRICES = Map.ofEntries(
        // --- Farming (1–2 coins) ---
        Map.entry(Items.WHEAT, 1),
        Map.entry(Items.CARROT, 1),
        Map.entry(Items.POTATO, 1),
        Map.entry(Items.BEETROOT, 1),
        Map.entry(Items.SUGAR_CANE, 1),
        Map.entry(Items.PUMPKIN, 1),
        Map.entry(Items.MELON_SLICE, 1),
        Map.entry(Items.BAMBOO, 1),
        Map.entry(Items.SWEET_BERRIES, 1),
        Map.entry(Items.GLOW_BERRIES, 1),
        Map.entry(Items.COCOA_BEANS, 2),
        Map.entry(Items.NETHER_WART, 2),

        // --- Animal Drops (1–10 coins) ---
        Map.entry(Items.EGG, 1),
        Map.entry(Items.WHITE_WOOL, 2),
        Map.entry(Items.ORANGE_WOOL, 2),
        Map.entry(Items.MAGENTA_WOOL, 2),
        Map.entry(Items.LIGHT_BLUE_WOOL, 2),
        Map.entry(Items.YELLOW_WOOL, 2),
        Map.entry(Items.LIME_WOOL, 2),
        Map.entry(Items.PINK_WOOL, 2),
        Map.entry(Items.GRAY_WOOL, 2),
        Map.entry(Items.LIGHT_GRAY_WOOL, 2),
        Map.entry(Items.CYAN_WOOL, 2),
        Map.entry(Items.PURPLE_WOOL, 2),
        Map.entry(Items.BLUE_WOOL, 2),
        Map.entry(Items.BROWN_WOOL, 2),
        Map.entry(Items.GREEN_WOOL, 2),
        Map.entry(Items.RED_WOOL, 2),
        Map.entry(Items.BLACK_WOOL, 2),
        Map.entry(Items.LEATHER, 3),
        Map.entry(Items.FEATHER, 3),
        Map.entry(Items.RABBIT_HIDE, 3),
        Map.entry(Items.HONEY_BOTTLE, 4),
        Map.entry(Items.RABBIT_FOOT, 8),
        Map.entry(Items.TURTLE_SCUTE, 10),

        // --- Basic Mining (1–6 coins) ---
        Map.entry(Items.COBBLESTONE, 1),
        Map.entry(Items.STONE, 1),
        Map.entry(Items.GRAVEL, 1),
        Map.entry(Items.FLINT, 2),
        Map.entry(Items.CLAY_BALL, 2),
        Map.entry(Items.COAL, 3),
        Map.entry(Items.QUARTZ, 4),
        Map.entry(Items.LAPIS_LAZULI, 6),

        // --- Metals & Gems (4–40 coins) ---
        Map.entry(Items.RAW_COPPER, 4),
        Map.entry(Items.COPPER_INGOT, 6),
        Map.entry(Items.RAW_IRON, 8),
        Map.entry(Items.IRON_INGOT, 15),
        Map.entry(Items.RAW_GOLD, 12),
        Map.entry(Items.GOLD_INGOT, 20),
        Map.entry(Items.DIAMOND, 40),

        // --- Rare / End-game (30–500 coins) ---
        Map.entry(Items.BLAZE_ROD, 30),
        Map.entry(Items.EMERALD, 50),
        Map.entry(Items.ENDER_PEARL, 60),
        Map.entry(Items.ECHO_SHARD, 80),
        Map.entry(Items.NETHERITE_SCRAP, 100),
        Map.entry(Items.SHULKER_SHELL, 150),
        Map.entry(Items.NETHERITE_INGOT, 200),
        Map.entry(Items.NETHER_STAR, 500),

        // --- Mob Drops (1–15 coins) ---
        Map.entry(Items.ROTTEN_FLESH, 1),
        Map.entry(Items.BONE, 2),
        Map.entry(Items.STRING, 3),
        Map.entry(Items.SPIDER_EYE, 4),
        Map.entry(Items.INK_SAC, 4),
        Map.entry(Items.GUNPOWDER, 5),
        Map.entry(Items.SLIME_BALL, 6),
        Map.entry(Items.GLOW_INK_SAC, 12),
        Map.entry(Items.PHANTOM_MEMBRANE, 15)
    );

    private ItemSellPriceRegistry() {}
}
