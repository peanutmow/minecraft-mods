package zeitvertreib.economy.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public final class MobEnchantedBookDropModifier {
	private static final ResourceKey<LootTable> WITCH = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("minecraft", "entities/witch"));
	private static final ResourceKey<LootTable> EVOKER = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("minecraft", "entities/evoker"));

	private MobEnchantedBookDropModifier() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register(MobEnchantedBookDropModifier::modifyLootTable);
	}

	private static void modifyLootTable(ResourceKey<LootTable> lootTableKey, LootTable.Builder tableBuilder, LootTableSource source, HolderLookup.Provider registries) {
		if (source != LootTableSource.VANILLA) {
			return;
		}

		float dropChance;
		if (WITCH.equals(lootTableKey)) {
			dropChance = zeitvertreib.economy.ZeitvertreibEconomy.CONFIG.getWitchEnchantedBookChance();
		} else if (EVOKER.equals(lootTableKey)) {
			dropChance = zeitvertreib.economy.ZeitvertreibEconomy.CONFIG.getEvokerEnchantedBookChance();
		} else {
			return;
		}

		LootPool.Builder poolBuilder = LootPool.lootPool()
			.setRolls(ConstantValue.exactly(1.0F))
			.when(LootItemRandomChanceCondition.randomChance(dropChance))
			.add(LootItem.lootTableItem(Items.ENCHANTED_BOOK).apply(EnchantWithLevelsFunction.enchantWithLevels(registries, ConstantValue.exactly(1.0F))));

		tableBuilder.withPool(poolBuilder);
	}
}
