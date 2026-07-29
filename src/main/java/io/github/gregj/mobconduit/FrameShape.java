package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The vanilla conduit frame: three orthogonal 5x5 rings around a centre block, 42 positions in
 * total.
 *
 * <p>The predicate is transcribed from {@code ConduitBlockEntity.updateShape} (26.2), which
 * walks offsets -2..2 on each axis and keeps a position when it is off the 3x3x3 core and lies
 * on one of the three rings. Vanilla's own water check on the core is deliberately not copied:
 * a Mob Conduit works in air.
 */
public final class FrameShape {
	/** Every position the shape can hold, and so vanilla's full-power frame count. */
	public static final int MAX_FRAME_BLOCKS = 42;

	/** Flat x,y,z triples. Flat rather than {@code BlockPos[]} to keep the scan allocation-free. */
	private static final int[] OFFSETS = buildOffsets();

	private FrameShape() {
	}

	private static int[] buildOffsets() {
		int[] offsets = new int[MAX_FRAME_BLOCKS * 3];
		int next = 0;

		for (int ox = -2; ox <= 2; ox++) {
			for (int oy = -2; oy <= 2; oy++) {
				for (int oz = -2; oz <= 2; oz++) {
					int ax = Math.abs(ox);
					int ay = Math.abs(oy);
					int az = Math.abs(oz);

					if ((ax > 1 || ay > 1 || az > 1)
							&& (ox == 0 && (ay == 2 || az == 2)
							|| oy == 0 && (ax == 2 || az == 2)
							|| oz == 0 && (ax == 2 || ay == 2))) {
						offsets[next++] = ox;
						offsets[next++] = oy;
						offsets[next++] = oz;
					}
				}
			}
		}

		if (next != offsets.length) {
			throw new IllegalStateException("conduit frame shape yielded " + next / 3 + " positions, expected " + MAX_FRAME_BLOCKS);
		}

		return offsets;
	}

	/**
	 * Flat x,y,z triples, one per frame position. Copied on the way out; the only caller is the
	 * {@code /mobconduit build} test command, so the allocation is irrelevant there and the hot
	 * path in {@link #count} keeps reading the array directly.
	 */
	public static int[] offsets() {
		return OFFSETS.clone();
	}

	/** Indexed, allocation-free access for callers on a tick path. */
	public static int offsetX(int index) {
		return OFFSETS[index * 3];
	}

	public static int offsetY(int index) {
		return OFFSETS[index * 3 + 1];
	}

	public static int offsetZ(int index) {
		return OFFSETS[index * 3 + 2];
	}

	/**
	 * Counts frame blocks around {@code centre}. The centre is the crystal's position, not the
	 * block it stands on.
	 */
	public static int count(Level level, BlockPos centre, Block frameBlock) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int centreX = centre.getX();
		int centreY = centre.getY();
		int centreZ = centre.getZ();
		int found = 0;

		for (int i = 0; i < OFFSETS.length; i += 3) {
			cursor.set(centreX + OFFSETS[i], centreY + OFFSETS[i + 1], centreZ + OFFSETS[i + 2]);

			if (level.getBlockState(cursor).getBlock() == frameBlock) {
				found++;
			}
		}

		return found;
	}
}
