package zeitvertreib.economy.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public final class DungeonLootBookInjector {
	private static final float DUNGEON_BOOK_CHANCE = 0.25F;

	private DungeonLootBookInjector() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register(DungeonLootBookInjector::modifyLootTable);
	}

	private static void modifyLootTable(
		ResourceKey<LootTable> lootTableKey,
		LootTable.Builder tableBuilder,
		LootTableSource source,
		HolderLookup.Provider registries
	) {
		if (source != LootTableSource.VANILLA || !BuiltInLootTables.SIMPLE_DUNGEON.equals(lootTableKey)) {
			return;
		}

		var enchantmentLookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		var randomLootEnchantments = enchantmentLookup.get(EnchantmentTags.ON_RANDOM_LOOT);
		if (randomLootEnchantments.isEmpty()) {
			return;
		}

		LootPool.Builder enchantedBookPool = LootPool.lootPool()
			.setRolls(ConstantValue.exactly(1.0F))
			.when(LootItemRandomChanceCondition.randomChance(DUNGEON_BOOK_CHANCE));

		randomLootEnchantments.get().forEach(enchantmentHolder -> enchantedBookPool.add(
			LootItem.lootTableItem(Items.ENCHANTED_BOOK)
				.apply(new SetEnchantmentsFunction.Builder()
					.withEnchantment(enchantmentHolder, ConstantValue.exactly(1.0F)))));

		tableBuilder.withPool(enchantedBookPool);
	}
}