package zeitvertreib.economy.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zeitvertreib.economy.ZeitvertreibEconomy;

@Mixin(Player.class)
public abstract class PlayerExperienceCapMixin {

	@Shadow public int experienceLevel;
	@Shadow public float experienceProgress;

	@Inject(method = "giveExperiencePoints", at = @At("RETURN"))
	private void zeitvertreib$capExperienceAfterPoints(int amount, CallbackInfo ci) {
		zeitvertreib$clampExperience();
	}

	@Inject(method = "giveExperienceLevels", at = @At("RETURN"))
	private void zeitvertreib$capExperienceAfterLevels(int levels, CallbackInfo ci) {
		zeitvertreib$clampExperience();
	}

	private void zeitvertreib$clampExperience() {
		int cap = ZeitvertreibEconomy.CONFIG.getMaxPlayerExperienceLevel();
		if (experienceLevel >= cap) {
			experienceLevel = cap;
			experienceProgress = 0.0F;
		}
	}
}
