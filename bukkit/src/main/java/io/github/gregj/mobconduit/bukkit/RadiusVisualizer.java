package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Particle;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a conduit's coverage volume in particles for a few seconds, so a builder can see the
 * edge before committing frame blocks. Pure packet work: {@code minecraft:glow} is registered
 * with {@code overrideLimiter = true} in vanilla, so the client never culls it, and it is
 * sent with {@code force = true} so the packets actually reach a player standing at the
 * structure — the server otherwise drops particle packets more than 32 blocks from their
 * point.
 *
 * <p>Every few ticks each visual draws one great circle, rotating through the three axis
 * planes, so the sphere reads as a sphere rather than a flat ring. Each point is a directed
 * count==0 packet with zero velocity — the only form the client places exactly.
 */
public final class RadiusVisualizer {
	private static final int DURATION_TICKS = 200;
	private static final int BAND_INTERVAL_TICKS = 5;
	private static final int POINTS_PER_BAND = 40;

	private static final class Visual {
		private final World world;
		private final ConduitPos centre;
		private final int radius;
		private final boolean cylindrical;
		private int ticksLeft;

		private Visual(World world, ConduitPos centre, int radius, boolean cylindrical, int ticksLeft) {
			this.world = world;
			this.centre = centre;
			this.radius = radius;
			this.cylindrical = cylindrical;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final List<Visual> ACTIVE = new ArrayList<>();

	private RadiusVisualizer() {
	}

	/** Arms one visual per conduit with a real radius. Returns how many. */
	public static int arm(World world, List<Conduit> conduits) {
		int armed = 0;

		for (Conduit conduit : conduits) {
			if (conduit.radius() > 0) {
				ACTIVE.add(new Visual(world, conduit.pos(), conduit.radius(), conduit.cylindrical(), DURATION_TICKS));
				armed++;
			}
		}

		return armed;
	}

	public static boolean isActive() {
		return !ACTIVE.isEmpty();
	}

	/**
	 * Drops every visual. Called on disable: visuals hold their {@link World}, so an uncleared
	 * list would pin a dead world in memory and keep the tick hook hot forever.
	 */
	public static void clearAll() {
		ACTIVE.clear();
	}

	/**
	 * Drops one world's visuals. Called on world unload: a visual's countdown only runs while
	 * its world ticks, so an unloaded world would park its visuals forever.
	 */
	public static void clearWorld(World world) {
		ACTIVE.removeIf(visual -> visual.world == world);
	}

	public static void tick(World world) {
		Iterator<Visual> it = ACTIVE.iterator();

		while (it.hasNext()) {
			Visual visual = it.next();

			if (visual.world != world) {
				continue;
			}

			if (--visual.ticksLeft <= 0) {
				it.remove();
				continue;
			}

			if (visual.ticksLeft % BAND_INTERVAL_TICKS == 0) {
				emitBand(world, visual);
			}
		}
	}

	private static void emitBand(World world, Visual visual) {
		double cx = visual.centre.centerX();
		double cy = visual.centre.centerY();
		double cz = visual.centre.centerZ();
		double r = visual.radius;

		if (visual.cylindrical) {
			emitCylinderBand(world, cx, cy, cz, r);
			return;
		}

		// Rotate through the planes (0 = xz, 1 = xy, 2 = yz) as the countdown runs down.
		int plane = (visual.ticksLeft / BAND_INTERVAL_TICKS) % 3;

		for (int i = 0; i < POINTS_PER_BAND; i++) {
			double angle = i * (Math.PI * 2.0 / POINTS_PER_BAND);
			double a = Math.cos(angle) * r;
			double b = Math.sin(angle) * r;
			double x = plane == 2 ? cx : cx + a;
			double y = plane == 0 ? cy : cy + b;
			double z = plane == 0 ? cz + a : (plane == 1 ? cz : cz + b);

			world.spawnParticle(Particle.GLOW, x, y, z, 0, 0.0, 0.0, 0.0, 1.0, null, true);
		}
	}

	/**
	 * A cylinder reads as three rings — crystal height and ±r, clamped into the world — plus
	 * vertical connectors at eight perimeter points, so the full-height column is visible.
	 */
	private static void emitCylinderBand(World world, double cx, double cy, double cz, double r) {
		// getMaxHeight is one past the topmost block, so the last reachable Y is one below it.
		double minY = world.getMinHeight();
		double maxY = world.getMaxHeight() - 1.0;
		double midY = Math.max(minY, Math.min(maxY, cy));
		double topY = Math.max(minY, Math.min(maxY, cy + r));
		double bottomY = Math.max(minY, Math.min(maxY, cy - r));

		for (double ringY : new double[] {midY, topY, bottomY}) {
			for (int i = 0; i < POINTS_PER_BAND; i++) {
				double angle = i * (Math.PI * 2.0 / POINTS_PER_BAND);
				world.spawnParticle(Particle.GLOW,
						cx + Math.cos(angle) * r, ringY, cz + Math.sin(angle) * r,
						0, 0.0, 0.0, 0.0, 1.0, null, true);
			}
		}

		for (int i = 0; i < 8; i++) {
			double angle = i * (Math.PI / 4.0);
			double x = cx + Math.cos(angle) * r;
			double z = cz + Math.sin(angle) * r;

			for (double y = bottomY; y <= topY; y += 8.0) {
				world.spawnParticle(Particle.GLOW, x, y, z, 0, 0.0, 0.0, 0.0, 1.0, null, true);
			}
		}
	}
}
