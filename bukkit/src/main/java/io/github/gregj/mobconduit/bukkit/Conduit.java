package io.github.gregj.mobconduit.bukkit;

/**
 * One active conduit. Keyed by the end crystal's {@link ConduitPos}; crystals do not move.
 *
 * <p>Only the position and frame count persist. The radius is derived from the frame count
 * through the live config, so editing radii and reloading re-derives every conduit without a
 * migration.
 */
public final class Conduit {
	private final ConduitPos pos;
	private final int frameCount;
	private final int radius;
	private final long radiusSq;
	private final boolean cylindrical;

	public Conduit(ConduitPos pos, int frameCount) {
		this.pos = pos;
		this.frameCount = frameCount;
		this.radius = ModConfig.get().radiusFor(frameCount);
		this.radiusSq = (long) this.radius * this.radius;
		this.cylindrical = ModConfig.get().radiusShape() == ModConfig.RadiusShape.CYLINDER;
	}

	public ConduitPos pos() {
		return this.pos;
	}

	public int frameCount() {
		return this.frameCount;
	}

	public int radius() {
		return this.radius;
	}

	public boolean cylindrical() {
		return this.cylindrical;
	}

	/**
	 * Spherical by default, matching how vanilla's conduit applies its own effect radius. A
	 * cylinder drops the Y term entirely: same horizontal radius, full height.
	 */
	public boolean covers(int x, int y, int z) {
		long dx = x - this.pos.x();
		long dz = z - this.pos.z();
		long horizontal = dx * dx + dz * dz;

		if (this.cylindrical) {
			return horizontal <= this.radiusSq;
		}

		long dy = y - this.pos.y();
		return horizontal + dy * dy <= this.radiusSq;
	}
}
