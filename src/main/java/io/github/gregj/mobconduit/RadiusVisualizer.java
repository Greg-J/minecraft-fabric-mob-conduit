package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a conduit's coverage volume in particles for a few seconds, so a builder can see the
 * edge before committing frame blocks. Pure packet work: {@code minecraft:glow} is registered
 * with {@code overrideLimiter = true} ({@code ParticleTypes.java:145}), so the client never
 * culls it, and it is sent with the server's matching flag so the packets actually reach a
 * player standing at the structure — the server otherwise drops particle packets more than 32
 * blocks from their point.
 *
 * <p>Every few ticks each visual draws one great circle, rotating through the three axis
 * planes, so the sphere reads as a sphere rather than a flat ring. Each point is a directed
 * count==0 packet with zero velocity — the only form the client places exactly
 * ({@code ClientPacketListener.handleParticleEvent}) — sent with the server's
 * {@code overrideLimiter} flag, because particle packets otherwise only reach players within
 * 32 blocks of the point ({@code ServerLevel.java:1318,1376}); the flag raises that to 512,
 * past the largest configurable radius.
 */
public final class RadiusVisualizer {
	private static final int DURATION_TICKS = 200;
	private static final int BAND_INTERVAL_TICKS = 10;

	/**
	 * Roughly one particle every {@value} blocks along a circle, rather than a fixed count. A
	 * flat 40 points put a speck every 20 blocks at the default 128 radius, which reads as
	 * nothing at all from the middle.
	 */
	private static final double BLOCKS_PER_POINT = 4.0;

	private static final int MIN_POINTS = 40;
	private static final int MAX_POINTS = 200;

	/** Points to draw a circle of this radius with roughly even, visible spacing. */
	private static int pointsFor(double radius) {
		int points = (int) Math.round(2.0 * Math.PI * radius / BLOCKS_PER_POINT);
		return Math.max(MIN_POINTS, Math.min(MAX_POINTS, points));
	}

	private static final class Visual {
		private final ServerLevel level;
		private final BlockPos centre;
		private final int radius;
		private final boolean cylindrical;
		private int ticksLeft;

		private Visual(ServerLevel level, BlockPos centre, int radius, boolean cylindrical, int ticksLeft) {
			this.level = level;
			this.centre = centre;
			this.radius = radius;
			this.cylindrical = cylindrical;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final List<Visual> ACTIVE = new ArrayList<>();

	private RadiusVisualizer() {
	}

	/**
	 * Arms one visual per conduit with a real radius. Returns how many.
	 *
	 * <p>Re-running the command restarts an existing visual rather than stacking a second one on
	 * the same conduit, which would double its particle rate for the overlap.
	 */
	public static int arm(ServerLevel level, List<Conduit> conduits) {
		int armed = 0;

		for (Conduit conduit : conduits) {
			if (conduit.radius() <= 0) {
				continue;
			}

			Visual existing = find(level, conduit.pos());

			if (existing != null) {
				existing.ticksLeft = DURATION_TICKS;
			} else {
				ACTIVE.add(new Visual(level, conduit.pos(), conduit.radius(), conduit.cylindrical(), DURATION_TICKS));
			}

			armed++;
		}

		return armed;
	}

	private static Visual find(ServerLevel level, BlockPos centre) {
		for (Visual visual : ACTIVE) {
			if (visual.level == level && visual.centre.equals(centre)) {
				return visual;
			}
		}

		return null;
	}

	public static boolean isActive() {
		return !ACTIVE.isEmpty();
	}

	/**
	 * Drops every visual. Called on server stop: visuals hold their {@link ServerLevel}, so an
	 * uncleared list would pin a dead level in memory and keep the tick hook hot forever.
	 */
	public static void clearAll() {
		ACTIVE.clear();
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
		double cx = visual.centre.getX() + 0.5;
		double cy = visual.centre.getY() + 0.5;
		double cz = visual.centre.getZ() + 0.5;
		double r = visual.radius;

		if (visual.cylindrical) {
			emitCylinderBand(level, cx, cy, cz, r);
			return;
		}

		// All three great circles every band, so the volume reads as a sphere straight away.
		// Rotating one plane per band meant the horizontal ring — the one a builder actually
		// wants — was only on screen a third of the time.
		int points = pointsFor(r);

		for (int i = 0; i < points; i++) {
			double angle = i * (Math.PI * 2.0 / points);
			double a = Math.cos(angle) * r;
			double b = Math.sin(angle) * r;

			// Each circle must vary two different axes. Driving two axes from the same term
			// collapses the ring into a diagonal line through the centre, which is what the
			// xz and yz planes used to do.
			emit(level, cx + a, cy, cz + b);  // horizontal, xz
			emit(level, cx + a, cy + b, cz);  // vertical, xy
			emit(level, cx, cy + a, cz + b);  // vertical, yz
		}
	}

	private static void emit(ServerLevel level, double x, double y, double z) {
		level.sendParticles(ParticleTypes.GLOW, true, false, x, y, z, 0, 0.0, 0.0, 0.0, 1.0);
	}

	/**
	 * A cylinder reads as three rings — crystal height and ±r, clamped into the world — plus
	 * vertical connectors at eight perimeter points, so the full-height column is visible.
	 */
	private static void emitCylinderBand(ServerLevel level, double cx, double cy, double cz, double r) {
		double minY = level.getMinY();
		double maxY = level.getMaxY();
		double midY = Math.max(minY, Math.min(maxY, cy));
		double topY = Math.max(minY, Math.min(maxY, cy + r));
		double bottomY = Math.max(minY, Math.min(maxY, cy - r));

		int points = pointsFor(r);

		for (double ringY : new double[] {midY, topY, bottomY}) {
			for (int i = 0; i < points; i++) {
				double angle = i * (Math.PI * 2.0 / points);
				emit(level, cx + Math.cos(angle) * r, ringY, cz + Math.sin(angle) * r);
			}
		}

		for (int i = 0; i < 8; i++) {
			double angle = i * (Math.PI / 4.0);
			double x = cx + Math.cos(angle) * r;
			double z = cz + Math.sin(angle) * r;

			for (double y = bottomY; y <= topY; y += 8.0) {
				emit(level, x, y, z);
			}
		}
	}
}
