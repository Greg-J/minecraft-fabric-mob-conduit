package io.github.gregj.mobconduit.mixin;

import io.github.gregj.mobconduit.SpawnOrigin;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Backfills the spawn reason for mobs Fabric's create-hook misses. Fabric only records the
 * reason on {@code EntityType.create(Level, EntitySpawnReason)}; spawners, trial spawners and
 * {@code /summon} go through the {@code EntitySpawnRequest} overload instead and record
 * nothing, and the village siege constructs its zombies directly. All of them still pass the
 * true reason to {@code finalizeSpawn} ({@code Mob.java:1079}), which is why this hook exists.
 * The body is a single call; the logic lives in {@link SpawnOrigin}.
 */
@Mixin(Mob.class)
public class MobMixin {
	@Inject(at = @At("HEAD"), method = "finalizeSpawn")
	private void mobconduit$recordSpawnReason(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> info) {
		SpawnOrigin.backfill((Mob) (Object) this, spawnReason);
	}
}
