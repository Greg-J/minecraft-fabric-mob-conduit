package io.github.gregj.mobconduit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * One active conduit. Keyed by the end crystal's {@link BlockPos}; crystals do not move.
 *
 * <p>Only the position and frame count persist. The radius is derived from the frame count
 * through the live config, so editing radii and reloading re-derives every conduit without a
 * migration.
 */
public final class Conduit {
	public static final Codec<Conduit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Conduit::pos),
			Codec.INT.fieldOf("frame_count").forGetter(Conduit::frameCount)
	).apply(instance, Conduit::new));

	private final BlockPos pos;
	private final int frameCount;
	private final int radius;
	private final long radiusSq;

	public Conduit(BlockPos pos, int frameCount) {
		this.pos = pos.immutable();
		this.frameCount = frameCount;
		this.radius = ModConfig.get().radiusFor(frameCount);
		this.radiusSq = (long) this.radius * this.radius;
	}

	public BlockPos pos() {
		return this.pos;
	}

	public int frameCount() {
		return this.frameCount;
	}

	public int radius() {
		return this.radius;
	}

	/** Spherical, matching how vanilla's conduit applies its own effect radius. */
	public boolean covers(int x, int y, int z) {
		long dx = x - this.pos.getX();
		long dy = y - this.pos.getY();
		long dz = z - this.pos.getZ();
		return dx * dx + dy * dy + dz * dz <= this.radiusSq;
	}
}
