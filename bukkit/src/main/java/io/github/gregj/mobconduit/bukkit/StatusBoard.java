package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * A live sidebar showing what the spawn guard is deciding, drawn with the vanilla scoreboard.
 *
 * <p>Scoreboards are plain server-side world data rather than registry entries, so this
 * renders on an unmodified client with nothing installed. That is the whole reason it is a
 * scoreboard and not a custom HUD.
 *
 * <p>Rows are ordered by score value, so each line carries an explicit rank and the counter
 * itself is shown in the row text.
 */
public final class StatusBoard {
	private static final String OBJECTIVE_NAME = "mobconduit";

	private static final int REFRESH_INTERVAL_TICKS = 20;

	private static boolean enabled;
	private static int tickCounter;
	private static String[] lastRows = new String[0];

	private StatusBoard() {
	}

	public static void enable() {
		enabled = true;
		tickCounter = 0;
		refresh(true);
	}

	public static void disable() {
		enabled = false;
		Scoreboard scoreboard = scoreboard();

		if (scoreboard != null) {
			Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);

			if (objective != null) {
				objective.unregister();
			}
		}

		lastRows = new String[0];
	}

	public static void tick() {
		if (!enabled) {
			return;
		}

		if (++tickCounter < REFRESH_INTERVAL_TICKS) {
			return;
		}

		tickCounter = 0;
		refresh(false);
	}

	private static Scoreboard scoreboard() {
		return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
	}

	private static void refresh(boolean force) {
		Scoreboard scoreboard = scoreboard();

		if (scoreboard == null) {
			return;
		}

		Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);

		if (objective == null) {
			objective = scoreboard.registerNewObjective(
					OBJECTIVE_NAME, Criteria.DUMMY, ChatColor.AQUA + "Mob Conduit");
			objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		}

		long attempts = SpawnStats.HOSTILE_NATURAL.get();
		long otherReason = SpawnStats.HOSTILE_OTHER_REASON.get();
		long suppressed = SpawnStats.SUPPRESSED.get();
		long outOfRange = SpawnStats.OUT_OF_RANGE.get();
		long noConduit = SpawnStats.SKIPPED_NO_ACTIVE.get();

		String[] rows = {
				ChatColor.GRAY + "non-natural hostiles: " + ChatColor.WHITE + otherReason,
				ChatColor.GRAY + "no active conduit: " + ChatColor.WHITE + noConduit,
				ChatColor.GRAY + "out of range: " + ChatColor.WHITE + outOfRange,
				ChatColor.GREEN + "suppressed: " + ChatColor.WHITE + suppressed,
				ChatColor.GRAY + "spawn attempts: " + ChatColor.WHITE + attempts,
				ChatColor.GOLD + "active conduits: " + ChatColor.WHITE + ConduitStore.totalActive(),
		};

		if (!force && sameRows(rows)) {
			return;
		}

		// Row text is the score entry, so changing a number means a new row. Clear the old
		// set rather than leaving stale lines stacked in the sidebar.
		for (String old : lastRows) {
			scoreboard.resetScores(old);
		}

		for (int i = 0; i < rows.length; i++) {
			objective.getScore(rows[i]).setScore(i);
		}

		lastRows = rows;
	}

	private static boolean sameRows(String[] rows) {
		if (lastRows.length != rows.length) {
			return false;
		}

		for (int i = 0; i < rows.length; i++) {
			if (!lastRows[i].equals(rows[i])) {
				return false;
			}
		}

		return true;
	}
}
