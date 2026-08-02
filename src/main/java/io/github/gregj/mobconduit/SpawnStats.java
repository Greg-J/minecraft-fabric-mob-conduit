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

	/**
	 * Hostiles allowed through because they arrived by a deliberate route — spawners, eggs,
	 * raids, trap horses. Broken out so a vanilla spawn path the reason filter has not
	 * considered shows up here instead of hiding behind a clean suppression rate, which is how
	 * the 26.2 jockey spawns went unnoticed.
	 */
	public static final AtomicLong HOSTILE_OTHER_REASON = new AtomicLong();

	/** Allowed because no conduit was active anywhere. */
	public static final AtomicLong SKIPPED_NO_ACTIVE = new AtomicLong();

	/** Allowed because the position fell outside every active conduit. */
	public static final AtomicLong OUT_OF_RANGE = new AtomicLong();

	/** Cancelled. */
	public static final AtomicLong SUPPRESSED = new AtomicLong();

	private SpawnStats() {
	}

	public static String summary() {
		return "hostile natural spawn attempts: " + HOSTILE_NATURAL.get()
				+ ", suppressed: " + SUPPRESSED.get()
				+ ", out of range: " + OUT_OF_RANGE.get()
				+ ", no active conduit: " + SKIPPED_NO_ACTIVE.get()
				+ ", non-natural hostiles allowed: " + HOSTILE_OTHER_REASON.get();
	}
}
