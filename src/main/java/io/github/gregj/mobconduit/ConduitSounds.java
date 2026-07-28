package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Beacon and conduit sounds layered together. Both are vanilla, so a vanilla client already has
 * them and nothing needs registering.
 *
 * <p>The pairs are pitched apart slightly so they read as one composite cue rather than two
 * sounds fired at the same instant.
 */
public final class ConduitSounds {
	/** Vanilla's conduit ambient cadence, {@code gameTime % 80} in {@code ConduitBlockEntity}. */
	public static final int AMBIENT_INTERVAL_TICKS = 80;

	private ConduitSounds() {
	}

	public static void activate(ServerLevel level, BlockPos pos) {
		if (!ModConfig.get().activationSounds()) {
			return;
		}

		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
	}

	public static void deactivate(ServerLevel level, BlockPos pos) {
		if (!ModConfig.get().activationSounds()) {
			return;
		}

		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.CONDUIT_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
	}

	public static void ambient(ServerLevel level, BlockPos pos) {
		if (!ModConfig.get().ambientSounds()) {
			return;
		}

		level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.1F);
	}
}
