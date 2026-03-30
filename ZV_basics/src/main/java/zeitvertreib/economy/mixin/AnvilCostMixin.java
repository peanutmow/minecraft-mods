package zeitvertreib.economy.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zeitvertreib.economy.ZeitvertreibEconomy;

@Mixin(AnvilMenu.class)
public abstract class AnvilCostMixin {

	@Shadow
	@Final
	private DataSlot cost;

	@ModifyConstant(method = "createResult", constant = @Constant(intValue = 40, ordinal = 1))
	private int zeitvertreib$disableRenameTooExpensiveCap(int original) {
		return Integer.MAX_VALUE;
	}

	@ModifyConstant(method = "createResult", constant = @Constant(intValue = 40, ordinal = 2))
	private int zeitvertreib$disableResultTooExpensiveCap(int original) {
		return Integer.MAX_VALUE;
	}

	@Inject(method = "createResult", at = @At("RETURN"))
	private void zeitvertreib$setConstantAnvilCost(CallbackInfo ci) {
		// cost > 0 means vanilla found a valid output; clamp it to our constant
		if (this.cost.get() > 0) {
			this.cost.set(ZeitvertreibEconomy.CONFIG.getAnvilXpCost());
		}
	}

	@Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
	private static void zeitvertreib$disableRepairCostGrowth(int currentRepairCost, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(0);
	}
}