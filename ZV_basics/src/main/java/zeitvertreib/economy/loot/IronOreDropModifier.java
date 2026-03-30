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
			if (stack.is(Items.RAW_IRON) || stack.is(Items.IRON_INGOT)) {
				toRemove.add(stack);
			}
		}
		generatedLoot.removeAll(toRemove);
	}
}
