package io.github.gregj.mobconduit.mixin;

import io.github.gregj.mobconduit.ConduitDetector;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only Mixin in the mod. An end crystal is an entity, so there is no block entity tick to
 * hang conduit detection on; this is the hook. The body stays a single call — all logic lives
 * in {@link ConduitDetector}, which throttles hard before doing any work.
 */
@Mixin(EndCrystal.class)
public class EndCrystalMixin {
	@Inject(at = @At("HEAD"), method = "tick")
	private void mobconduit$onTick(CallbackInfo info) {
		ConduitDetector.onCrystalTick((EndCrystal) (Object) this);
	}
}
