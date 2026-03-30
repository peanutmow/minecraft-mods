package zeitvertreib.economy.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zeitvertreib.economy.ZeitvertreibEconomy;

@Mixin(VillagerTrades.EnchantBookForEmeralds.class)
public class LibrarianEnchantedBookTradeMixin {
	@Inject(method = "getOffer", at = @At("HEAD"), cancellable = true)
	private void zeitvertreib$disableLibrarianEnchantedBookOffers(
		ServerLevel level,
		Entity entity,
		RandomSource random,
		CallbackInfoReturnable<MerchantOffer> cir
	) {
		if (ZeitvertreibEconomy.CONFIG.isBlockLibrarianBookTrades()
				&& entity instanceof Villager villager
				&& villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
			cir.setReturnValue(null);
		}
	}
}