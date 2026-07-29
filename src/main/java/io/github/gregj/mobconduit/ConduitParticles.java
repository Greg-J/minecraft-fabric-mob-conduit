package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Particle work that belongs to the conduit itself rather than to a mob being erased.
 *
 * <p>Everything here is a vanilla particle emitted with a plain world method call. No particle
 * type is registered, so a vanilla client renders all of it with nothing installed.
 */
public final class ConduitParticles {
	/**
	 * Height above the crystal that risers launch from. The frame's top ring sits at +2, so this
	 * clears it and reads as coming off the top of the structure.
	 */
	private static final int TOP_OFFSET = 3;

	private ConduitParticles() {
	}

	/**
	 * A continuous shimmer in and around the crystal while the conduit is active.
	 *
	 * <p>{@code trial_spawner_detection_ominous} is registered with {@code overrideLimiter =
	 * true} ({@code ParticleTypes.java:156}), so unlike most particles it stays visible at
	 * distance and is not culled by the client's particle setting — which is what makes it
	 * usable as a permanent "this thing is on" marker rather than a one-off flourish.
	 */
	public static void crystalAura(ServerLevel level, BlockPos crystalPos) {
		ModConfig config = ModConfig.get();
		int count = config.crystalAuraCount();

		if (count <= 0) {
			return;
		}

		RandomSource random = level.getRandom();
		double cx = crystalPos.getX() + 0.5;
		double cy = crystalPos.getY() + 0.6;
		double cz = crystalPos.getZ() + 0.5;

		for (int i = 0; i < count; i++) {
			// Direction sampled on a sphere, so the aura sits evenly around the crystal rather
			// than bunching at the poles the way independent per-axis randoms would.
			double theta = random.nextDouble() * Math.PI * 2.0;
			double z = random.nextDouble() * 2.0 - 1.0;
			double r = Math.sqrt(1.0 - z * z);
			double dx = r * Math.cos(theta);
			double dy = r * Math.sin(theta);
			double dz = z;

			// Radius varies so some sit inside the crystal and some drift just outside it.
			double radius = 0.15 + random.nextDouble() * 0.75;
			double drift = 0.02 + random.nextDouble() * 0.05;

			sendDirected(level, config.crystalAuraParticle(),
					cx + dx * radius, cy + dy * radius, cz + dz * radius,
					dx * drift, dy * drift, dz * drift);
		}
	}

	/**
	 * Soul flames erupting off the top of the conduit, marking a kill the structure is
	 * responsible for.
	 *
	 * <p>Spread across the 3x3 of blocks centred on the crystal rather than a single column, so
	 * it reads as the whole structure venting rather than a thin jet.
	 */
	public static void killPlume(ServerLevel level, BlockPos crystalPos) {
		ModConfig config = ModConfig.get();
		int count = config.killPlumeCount();

		if (count <= 0) {
			return;
		}

		RandomSource random = level.getRandom();
		double baseY = crystalPos.getY() + TOP_OFFSET;

		for (int i = 0; i < count; i++) {
			// Random block within the centre 3x3, then a random point inside that block.
			double x = crystalPos.getX() + random.nextInt(3) - 1 + random.nextDouble();
			double z = crystalPos.getZ() + random.nextInt(3) - 1 + random.nextDouble();
			double y = baseY + (random.nextDouble() - 0.5) * 0.5;
			double rise = config.removalRiserSpeed() * (0.7 + random.nextDouble() * 0.6);

			sendDirected(level, config.killPlumeParticle(), x, y, z,
					(random.nextDouble() - 0.5) * 0.08, rise, (random.nextDouble() - 0.5) * 0.08);
		}
	}

	/**
	 * A column fired straight up out of the top of the conduit.
	 *
	 * <p>Built the way the warden builds its sonic boom ({@code SonicBoom.java:72-75}): one
	 * particle per block along the line, placed with no velocity at all. That matters for
	 * {@code sonic_boom} specifically — {@code SonicBoomParticle} is {@code quadSize = 1.5},
	 * roughly ten times a normal particle, and it animates in place rather than travelling. Give
	 * it velocity or emit a scattered cloud of it and you get a wall of white instead of a beam.
	 */
	public static void killBeam(ServerLevel level, BlockPos crystalPos) {
		ModConfig config = ModConfig.get();
		int length = config.killBeamLength();

		if (length <= 0) {
			return;
		}

		double x = crystalPos.getX() + 0.5;
		double z = crystalPos.getZ() + 0.5;
		double baseY = crystalPos.getY() + TOP_OFFSET;

		for (int i = 0; i < length; i++) {
			level.sendParticles(config.killBeamParticle(), x, baseY + i, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Obsidian tears weeping off the frame.
	 *
	 * <p>Positioning mirrors {@code CryingObsidianBlock.animateTick}: pick a random face other
	 * than up, offset 0.6 past it, and emit with no velocity so the client's hang-then-fall
	 * provider takes over. Only a handful of frame blocks drip per pass — all 42 at once reads
	 * as a wall of particles rather than weeping stone.
	 */
	public static void frameDrips(ServerLevel level, BlockPos crystalPos) {
		ModConfig config = ModConfig.get();
		int drips = config.frameDripCount();

		if (drips <= 0) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < drips; i++) {
			int index = random.nextInt(FrameShape.MAX_FRAME_BLOCKS);
			double bx = crystalPos.getX() + FrameShape.offsetX(index);
			double by = crystalPos.getY() + FrameShape.offsetY(index);
			double bz = crystalPos.getZ() + FrameShape.offsetZ(index);

			Direction face = Direction.getRandom(random);

			if (face == Direction.UP) {
				face = Direction.DOWN;
			}

			double ox = face.getStepX() == 0 ? random.nextDouble() : 0.5 + face.getStepX() * 0.6;
			double oy = face.getStepY() == 0 ? random.nextDouble() : 0.5 + face.getStepY() * 0.6;
			double oz = face.getStepZ() == 0 ? random.nextDouble() : 0.5 + face.getStepZ() * 0.6;

			sendDirected(level, config.frameDripParticle(), bx + ox, by + oy, bz + oz, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Sends one particle with an explicit velocity.
	 *
	 * <p>A count of zero is what makes the client read the three distance fields as a velocity
	 * vector instead of random offsets ({@code ClientPacketListener.handleParticleEvent}). It is
	 * the only way to aim a particle from the server.
	 */
	private static void sendDirected(ServerLevel level, ParticleOptions particle,
			double x, double y, double z, double dx, double dy, double dz) {
		level.sendParticles(particle, x, y, z, 0, dx, dy, dz, 1.0);
	}
}
