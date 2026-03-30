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

public final class DiamondOreDropModifier {
	private DiamondOreDropModifier() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register(DiamondOreDropModifier::modifyDiamondOreDrops);
	}

	private static void modifyDiamondOreDrops(Holder<LootTable> lootTableHolder, LootContext context, List<ItemStack> generatedLoot) {
		// Filter diamonds from any loot table with a configurable chance
		// This will apply to all diamond ore drops
		float diamondDropChance = zeitvertreib.economy.ZeitvertreibEconomy.CONFIG.getDiamondDropChance();
		RandomSource random = context.getRandom();

		List<ItemStack> toRemove = new ArrayList<>();
		for (ItemStack stack : generatedLoot) {
			if (stack.is(Items.DIAMOND)) {
				if (random.nextFloat() > diamondDropChance) {
					toRemove.add(stack);
				}
			}
		}

		generatedLoot.removeAll(toRemove);
	}
}
