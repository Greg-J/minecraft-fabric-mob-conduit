package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a conduit's coverage sphere in particles for a few seconds, so a builder can see the
 * edge before committing frame blocks. Pure packet work: {@code minecraft:glow} is registered
 * with {@code overrideLimiter = true} ({@code ParticleTypes.java:145}), so the ring stays
 * visible at 128 blocks and ignores the client's particle setting.
 *
 * <p>Every few ticks each visual draws one great circle, rotating through the three axis
 * planes, so the sphere reads as a sphere rather than a flat ring. Each point is a directed
 * count==0 packet with zero velocity — the only form the client places exactly
 * ({@code ClientPacketListener.handleParticleEvent}).
 */
public final class RadiusVisualizer {
	private static final int DURATION_TICKS = 200;
	private static final int BAND_INTERVAL_TICKS = 5;
	private static final int POINTS_PER_BAND = 40;

	private static final class Visual {
		private final ServerLevel level;
		private final BlockPos centre;
		private final int radius;
		private int ticksLeft;

		private Visual(ServerLevel level, BlockPos centre, int radius, int ticksLeft) {
			this.level = level;
			this.centre = centre;
			this.radius = radius;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final List<Visual> ACTIVE = new ArrayList<>();

	private RadiusVisualizer() {
	}

	/** Arms one visual per conduit with a real radius. Returns how many. */
	public static int arm(ServerLevel level, List<Conduit> conduits) {
		int armed = 0;

		for (Conduit conduit : conduits) {
			if (conduit.radius() > 0) {
				ACTIVE.add(new Visual(level, conduit.pos(), conduit.radius(), DURATION_TICKS));
				armed++;
			}
		}

		return armed;
	}

	public static boolean isActive() {
		return !ACTIVE.isEmpty();
	}

	public static void tick(ServerLevel level) {
		Iterator<Visual> it = ACTIVE.iterator();

		while (it.hasNext()) {
			Visual visual = it.next();

			if (visual.level != level) {
				continue;
			}

			if (--visual.ticksLeft <= 0) {
				it.remove();
				continue;
			}

			if (visual.ticksLeft % BAND_INTERVAL_TICKS == 0) {
				emitBand(level, visual);
			}
		}
	}

	private static void emitBand(ServerLevel level, Visual visual) {
		// Rotate through the planes: xz first, then xy, then yz.
		int plane = (visual.ticksLeft / BAND_INTERVAL_TICKS) % 3;
		double cx = visual.centre.getX() + 0.5;
		double cy = visual.centre.getY() + 0.5;
		double cz = visual.centre.getZ() + 0.5;
		double r = visual.radius;

		for (int i = 0; i < POINTS_PER_BAND; i++) {
			double angle = i * (Math.PI * 2.0 / POINTS_PER_BAND);
			double a = Math.cos(angle) * r;
			double b = Math.sin(angle) * r;
			double x = plane == 2 ? cx : cx + a;
			double y = plane == 0 ? cy : cy + b;
			double z = plane == 0 ? cz + a : (plane == 1 ? cz : cz + b);

			level.sendParticles(ParticleTypes.GLOW, x, y, z, 0, 0.0, 0.0, 0.0, 1.0);
		}
	}
}
