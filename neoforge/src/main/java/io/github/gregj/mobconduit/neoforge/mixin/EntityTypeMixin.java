package io.github.gregj.mobconduit.neoforge.mixin;

import io.github.gregj.mobconduit.neoforge.NeoForgeSpawnReasons;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the reason passed to {@code EntityType.create(Level, EntitySpawnReason)}
 * (EntityType.java:300) — the capture Fabric API does for the Fabric build via
 * {@code EntityLoadData}. Only this overload is hooked, exactly like Fabric: the reason
 * overload delegates into the {@code EntitySpawnRequest} overload, so spawners, trial spawners
 * and {@code /summon} record nothing, and {@code SpawnOrigin}'s backfill covers those. The
 * body is a single call; the logic lives in {@link NeoForgeSpawnReasons}.
 *
 * <p>Noted for maintainers: NeoForge's own patched {@code Mob.finalizeSpawn} stores the
 * reason on the mob ({@code Mob#getSpawnType()}), which could replace this capture for mobs.
 * This mixin stays because it mirrors Fabric's capture exactly and covers non-{@code Mob}
 * entities uniformly, but if the map ever causes trouble, that is the simpler path.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
	@Inject(at = @At("RETURN"), method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;")
	private void mobconduit$recordSpawnReason(Level level, EntitySpawnReason reason, CallbackInfoReturnable<Entity> info) {
		NeoForgeSpawnReasons.record(info.getReturnValue(), reason);
	}
}
