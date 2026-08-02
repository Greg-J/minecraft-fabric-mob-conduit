package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;

/**
 * Beacon and conduit sounds layered together. Both are vanilla, so a vanilla client already
 * has them and nothing needs registering.
 *
 * <p>The pairs are pitched apart slightly so they read as one composite cue rather than two
 * sounds fired at the same instant.
 */
public final class ConduitSounds {
	/** Vanilla's conduit ambient cadence, {@code gameTime % 80} in {@code ConduitBlockEntity}. */
	public static final int AMBIENT_INTERVAL_TICKS = 80;

	private ConduitSounds() {
	}

	public static void activate(World world, ConduitPos pos) {
		if (!ModConfig.get().activationSounds()) {
			return;
		}

		Location location = new Location(world, pos.centerX(), pos.centerY(), pos.centerZ());
		world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.playSound(location, Sound.BLOCK_CONDUIT_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.2F);
	}

	public static void deactivate(World world, ConduitPos pos) {
		if (!ModConfig.get().activationSounds()) {
			return;
		}

		Location location = new Location(world, pos.centerX(), pos.centerY(), pos.centerZ());
		world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.playSound(location, Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.2F);
	}

	public static void ambient(World world, ConduitPos pos) {
		if (!ModConfig.get().ambientSounds()) {
			return;
		}

		Location location = new Location(world, pos.centerX(), pos.centerY(), pos.centerZ());
		world.playSound(location, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.playSound(location, Sound.BLOCK_CONDUIT_AMBIENT, SoundCategory.BLOCKS, 0.8F, 1.1F);
	}
}
