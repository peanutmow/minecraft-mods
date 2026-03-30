package zeitvertreib.economy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zeitvertreib.economy.ZeitvertreibEconomy;

@Mixin(NetherPortalBlock.class)
public class NetherPortalMixin {

	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void zeitvertreib$blockNetherPortalTravel(
		BlockState state, Level level, BlockPos pos, Entity entity,
		InsideBlockEffectApplier applier, boolean bl, CallbackInfo ci
	) {
		if (!ZeitvertreibEconomy.CONFIG.isNetherPortalsEnabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "getPortalTransitionTime", at = @At("HEAD"), cancellable = true)
	private void zeitvertreib$blockNetherPortalInitiation(
		ServerLevel level, Entity entity, CallbackInfoReturnable<Integer> cir
	) {
		if (!ZeitvertreibEconomy.CONFIG.isNetherPortalsEnabled()) {
			cir.setReturnValue(Integer.MAX_VALUE);
		}
	}
}
