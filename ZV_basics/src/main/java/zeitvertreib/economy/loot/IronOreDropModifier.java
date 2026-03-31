package zeitvertreib.economy.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

public final class IronOreDropModifier {
	private IronOreDropModifier() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register(IronOreDropModifier::modifyIronOreDrops);
	}

	private static void modifyIronOreDrops(Holder<LootTable> lootTableHolder, LootContext context, List<ItemStack> generatedLoot) {
		if (zeitvertreib.economy.ZeitvertreibEconomy.CONFIG.isIronEnabled()) {
			return;
		}

		List<ItemStack> toRemove = new ArrayList<>();
		for (ItemStack stack : generatedLoot) {
			if (isIronItem(stack)) {
				toRemove.add(stack);
			}
		}
		generatedLoot.removeAll(toRemove);
	}

	private static boolean isIronItem(ItemStack stack) {
		return stack.is(Items.RAW_IRON)
			|| stack.is(Items.RAW_IRON_BLOCK)
			|| stack.is(Items.IRON_INGOT)
			|| stack.is(Items.IRON_NUGGET)
			|| stack.is(Items.IRON_SWORD)
			|| stack.is(Items.IRON_PICKAXE)
			|| stack.is(Items.IRON_AXE)
			|| stack.is(Items.IRON_SHOVEL)
			|| stack.is(Items.IRON_HOE)
			|| stack.is(Items.IRON_HELMET)
			|| stack.is(Items.IRON_CHESTPLATE)
			|| stack.is(Items.IRON_LEGGINGS)
			|| stack.is(Items.IRON_BOOTS)
			|| stack.is(Items.IRON_HORSE_ARMOR)
			|| stack.is(Items.CHAINMAIL_HELMET)
			|| stack.is(Items.CHAINMAIL_CHESTPLATE)
			|| stack.is(Items.CHAINMAIL_LEGGINGS)
			|| stack.is(Items.CHAINMAIL_BOOTS);
	}
}
