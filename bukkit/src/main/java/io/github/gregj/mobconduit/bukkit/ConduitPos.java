package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * An immutable block position. Stands in for vanilla's {@code BlockPos}, which the Bukkit API
 * does not expose; conduit state is keyed by the end crystal's block position, and crystals do
 * not move.
 */
public record ConduitPos(int x, int y, int z) {
	public static ConduitPos of(Entity entity) {
		Location location = entity.getLocation();
		return new ConduitPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	public ConduitPos below() {
		return new ConduitPos(this.x, this.y - 1, this.z);
	}

	/** The block's centre, where effects anchored on this position emit from. */
	public double centerX() {
		return this.x + 0.5;
	}

	public double centerY() {
		return this.y + 0.5;
	}

	public double centerZ() {
		return this.z + 0.5;
	}

	@Override
	public String toString() {
		return this.x + " " + this.y + " " + this.z;
	}
}
