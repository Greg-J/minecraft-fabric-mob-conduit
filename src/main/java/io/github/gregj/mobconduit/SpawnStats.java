package io.github.gregj.mobconduit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for what the spawn guard actually sees and decides, surfaced by
 * {@code /mobconduit status}.
 *
 * <p>These exist because "hostiles are still appearing" is not diagnosable from a mob count:
 * it cannot distinguish the guard never running, the guard running and declining to suppress,
 * or the guard working correctly on mobs that arrived some other way. Each counter separates
 * one of those cases.
 *
 * <p>Atomic because entity loading is not confined to the server thread.
 */
public final class SpawnStats {
	/** Natural hostile spawn attempts that reached the guard at all. */
	public static final AtomicLong HOSTILE_NATURAL = new AtomicLong();

	/** Allowed because no conduit was active anywhere. */
	public static final AtomicLong SKIPPED_NO_ACTIVE = new AtomicLong();

	/** Allowed because the position fell outside every active conduit. */
	public static final AtomicLong OUT_OF_RANGE = new AtomicLong();

	/** Cancelled. */
	public static final AtomicLong SUPPRESSED = new AtomicLong();

	private SpawnStats() {
	}

	public static void reset() {
		HOSTILE_NATURAL.set(0);
		SKIPPED_NO_ACTIVE.set(0);
		OUT_OF_RANGE.set(0);
		SUPPRESSED.set(0);
	}

	public static String summary() {
		return "hostile natural spawn attempts: " + HOSTILE_NATURAL.get()
				+ ", suppressed: " + SUPPRESSED.get()
				+ ", out of range: " + OUT_OF_RANGE.get()
				+ ", no active conduit: " + SKIPPED_NO_ACTIVE.get();
	}
}
