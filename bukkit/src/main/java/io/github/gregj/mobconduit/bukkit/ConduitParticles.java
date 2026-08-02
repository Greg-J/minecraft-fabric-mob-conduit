package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Random;

/**
 * Particle work that belongs to the conduit itself rather than to a mob being erased.
 *
 * <p>Everything here is a vanilla particle emitted with a plain world method call. No
 * particle type is registered, so a vanilla client renders all of it with nothing installed.
 */
public final class ConduitParticles {
	/**
	 * Height above the crystal that risers launch from. The frame's top ring sits at +2, so
	 * this clears it and reads as coming off the top of the structure.
	 */
	private static final int TOP_OFFSET = 3;

	/** Server-thread only; stands in for the level's {@code RandomSource}. */
	private static final Random RANDOM = new Random();

	/** Six face steps, indexed like vanilla's {@code Direction}: down, up, north, south, west, east. */
	private static final int[][] FACES = {
			{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
	};

	private ConduitParticles() {
	}

	/**
	 * A continuous shimmer in and around the crystal while the conduit is active.
	 *
	 * <p>Two things make it usable as a permanent "this thing is on" marker. The particle
	 * type ({@code trial_spawner_detection_ominous} by default) is registered with
	 * {@code overrideLimiter = true} in vanilla, so the client never culls it — not at
	 * distance, not on Decreased particle settings. And it is sent with {@code force = true},
	 * which raises the server's particle send gate from 32 to 512 blocks.
	 */
	public static void crystalAura(World world, ConduitPos crystalPos) {
		ModConfig config = ModConfig.get();
		int count = config.crystalAuraCount();

		if (count <= 0) {
			return;
		}

		double cx = crystalPos.centerX();
		double cy = crystalPos.y() + 0.6;
		double cz = crystalPos.centerZ();

		for (int i = 0; i < count; i++) {
			// Direction sampled on a sphere, so the aura sits evenly around the crystal
			// rather than bunching at the poles the way independent per-axis randoms would.
			double theta = RANDOM.nextDouble() * Math.PI * 2.0;
			double z = RANDOM.nextDouble() * 2.0 - 1.0;
			double r = Math.sqrt(1.0 - z * z);
			double dx = r * Math.cos(theta);
			double dy = r * Math.sin(theta);
			double dz = z;

			// Radius varies so some sit inside the crystal and some drift just outside it.
			double radius = 0.15 + RANDOM.nextDouble() * 0.75;
			double drift = 0.02 + RANDOM.nextDouble() * 0.05;

			sendDirected(world, config.crystalAuraParticle(), true,
					cx + dx * radius, cy + dy * radius, cz + dz * radius,
					dx * drift, dy * drift, dz * drift);
		}
	}

	/**
	 * Soul flames erupting off the top of the conduit, marking a kill the structure is
	 * responsible for.
	 *
	 * <p>Spread across the 3x3 of blocks centred on the crystal rather than a single column,
	 * so it reads as the whole structure venting rather than a thin jet.
	 */
	public static void killPlume(World world, ConduitPos crystalPos) {
		ModConfig config = ModConfig.get();
		int count = config.killPlumeCount();

		if (count <= 0) {
			return;
		}

		double baseY = crystalPos.y() + TOP_OFFSET;

		for (int i = 0; i < count; i++) {
			// Random block within the centre 3x3, then a random point inside that block.
			double x = crystalPos.x() + RANDOM.nextInt(3) - 1 + RANDOM.nextDouble();
			double z = crystalPos.z() + RANDOM.nextInt(3) - 1 + RANDOM.nextDouble();
			double y = baseY + (RANDOM.nextDouble() - 0.5) * 0.5;
			double rise = config.removalRiserSpeed() * (0.7 + RANDOM.nextDouble() * 0.6);

			sendDirected(world, config.killPlumeParticle(), true, x, y, z,
					(RANDOM.nextDouble() - 0.5) * 0.08, rise, (RANDOM.nextDouble() - 0.5) * 0.08);
		}
	}

	/**
	 * A column fired straight up out of the top of the conduit.
	 *
	 * <p>Built the way the warden builds its sonic boom: one particle per block along the
	 * line, placed with no velocity at all. That matters for {@code sonic_boom} specifically —
	 * it is roughly ten times a normal particle's size and animates in place rather than
	 * travelling. Give it velocity or emit a scattered cloud of it and you get a wall of
	 * white instead of a beam.
	 */
	public static void killBeam(World world, ConduitPos crystalPos) {
		ModConfig config = ModConfig.get();
		int length = config.killBeamLength();

		if (length <= 0) {
			return;
		}

		double x = crystalPos.centerX();
		double z = crystalPos.centerZ();
		double baseY = crystalPos.y() + TOP_OFFSET;

		for (int i = 0; i < length; i++) {
			world.spawnParticle(config.killBeamParticle(), x, baseY + i, z, 1, 0.0, 0.0, 0.0, 0.0, null, true);
		}
	}

	/**
	 * Obsidian tears weeping off the frame.
	 *
	 * <p>Positioning mirrors {@code CryingObsidianBlock.animateTick}: pick a random face
	 * other than up, offset 0.6 past it, and emit with no velocity so the client's
	 * hang-then-fall provider takes over. Only a handful of frame blocks drip per pass — all
	 * 42 at once reads as a wall of particles rather than weeping stone.
	 */
	public static void frameDrips(World world, ConduitPos crystalPos) {
		ModConfig config = ModConfig.get();
		int drips = config.frameDripCount();

		if (drips <= 0) {
			return;
		}

		for (int i = 0; i < drips; i++) {
			int index = RANDOM.nextInt(FrameShape.MAX_FRAME_BLOCKS);
			double bx = crystalPos.x() + FrameShape.offsetX(index);
			double by = crystalPos.y() + FrameShape.offsetY(index);
			double bz = crystalPos.z() + FrameShape.offsetZ(index);

			int[] face = FACES[RANDOM.nextInt(FACES.length)];

			if (face[1] == 1) {
				face = FACES[0]; // up reads as down, like vanilla
			}

			double ox = face[0] == 0 ? RANDOM.nextDouble() : 0.5 + face[0] * 0.6;
			double oy = face[1] == 0 ? RANDOM.nextDouble() : 0.5 + face[1] * 0.6;
			double oz = face[2] == 0 ? RANDOM.nextDouble() : 0.5 + face[2] * 0.6;

			sendDirected(world, config.frameDripParticle(), false, bx + ox, by + oy, bz + oz, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Sends one particle with an explicit velocity.
	 *
	 * <p>A count of zero is what makes the client read the three offset fields as a velocity
	 * vector instead of random offsets. It is the only way to aim a particle from the server.
	 *
	 * <p>{@code longReach} maps to the {@code force} flag, which raises the packet send gate
	 * from 32 to 512 blocks. Ambient decor (frame drips) stays short-reach so distant players
	 * are not spammed; anything whose purpose is to be seen from across the radius goes long.
	 */
	private static void sendDirected(World world, Particle particle, boolean longReach,
			double x, double y, double z, double dx, double dy, double dz) {
		world.spawnParticle(particle, x, y, z, 0, dx, dy, dz, 1.0, null, longReach);
	}
}
