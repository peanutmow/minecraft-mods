package zeitvertreib.economy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zeitvertreib.economy.ZeitvertreibEconomy;

@Mixin(EndPortalBlock.class)
public class EndPortalMixin {

	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void zeitvertreib$blockEndPortalTravel(
		BlockState state, Level level, BlockPos pos, Entity entity,
		InsideBlockEffectApplier applier, boolean bl, CallbackInfo ci
	) {
		if (!ZeitvertreibEconomy.CONFIG.isEndEnabled()) {
			ci.cancel();
		}
	}
}
