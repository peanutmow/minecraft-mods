package zeitvertreib.economy.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.ArrayList;
import java.util.List;

public final class DungeonLootBookInjector {
	private static final String CHEST_TABLE_PATH_PREFIX = "chests/";

	private DungeonLootBookInjector() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register(DungeonLootBookInjector::modifyLootTable);
		LootTableEvents.MODIFY_DROPS.register(DungeonLootBookInjector::modifyLootDrops);
	}

	private static void modifyLootTable(
		ResourceKey<LootTable> lootTableKey,
		LootTable.Builder tableBuilder,
		LootTableSource source,
		HolderLookup.Provider registries
	) {
		if (source != LootTableSource.VANILLA || !isChestTable(lootTableKey)) {
			return;
		}

		var enchantmentLookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		var allEnchantments = enchantmentLookup.listElements().toList();
		if (allEnchantments.isEmpty()) {
			return;
		}

		LootPool.Builder enchantedBookPool = LootPool.lootPool()
			.setRolls(ConstantValue.exactly(1.0F))
			.when(LootItemRandomChanceCondition.randomChance(zeitvertreib.economy.ZeitvertreibEconomy.CONFIG.getDungeonBookChance()));

		for (Holder<Enchantment> enchantmentHolder : allEnchantments) {
			enchantedBookPool.add(
				LootItem.lootTableItem(Items.ENCHANTED_BOOK)
					.apply(EnchantRandomlyFunction.randomEnchantment()
						.withEnchantment(enchantmentHolder)
						.allowingIncompatibleEnchantments())
			);
		}

		tableBuilder.withPool(enchantedBookPool);
	}

	private static void modifyLootDrops(Holder<LootTable> lootTableHolder, LootContext context, List<ItemStack> generatedLoot) {
		if (lootTableHolder.unwrapKey().filter(DungeonLootBookInjector::isChestTable).isEmpty()) {
			return;
		}

		for (ItemStack stack : generatedLoot) {
			if (!stack.is(Items.ENCHANTED_BOOK)) {
				continue;
			}
			clampEnchantmentsToLevelOne(stack);
		}
	}

	private static boolean isChestTable(ResourceKey<LootTable> lootTableKey) {
		Identifier id = lootTableKey.identifier();
		return "minecraft".equals(id.getNamespace()) && id.getPath().startsWith(CHEST_TABLE_PATH_PREFIX);
	}

	private static void clampEnchantmentsToLevelOne(ItemStack stack) {
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored == null || stored.isEmpty()) return;
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stored);
		List<Holder<Enchantment>> enchantments = new ArrayList<>(mutable.keySet());
		for (Holder<Enchantment> enchantmentHolder : enchantments) {
			mutable.set(enchantmentHolder, 1);
		}
		stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
	}
}