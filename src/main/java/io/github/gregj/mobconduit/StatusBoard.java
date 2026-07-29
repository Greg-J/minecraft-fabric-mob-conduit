package io.github.gregj.mobconduit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * A live sidebar showing what the spawn guard is deciding, drawn with the vanilla scoreboard.
 *
 * <p>Scoreboards are plain server-side world data rather than registry entries, so this renders
 * on an unmodified client with nothing installed. That is the whole reason it is a scoreboard
 * and not a custom HUD.
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

	public static boolean isEnabled() {
		return enabled;
	}

	public static void enable(MinecraftServer server) {
		enabled = true;
		tickCounter = 0;
		refresh(server, true);
	}

	public static void disable(MinecraftServer server) {
		enabled = false;
		ServerScoreboard scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);

		if (objective != null) {
			scoreboard.removeObjective(objective);
		}

		lastRows = new String[0];
	}

	public static void tick(MinecraftServer server) {
		if (!enabled) {
			return;
		}

		if (++tickCounter < REFRESH_INTERVAL_TICKS) {
			return;
		}

		tickCounter = 0;
		refresh(server, false);
	}

	private static void refresh(MinecraftServer server, boolean force) {
		ServerScoreboard scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);

		if (objective == null) {
			objective = scoreboard.addObjective(
					OBJECTIVE_NAME,
					ObjectiveCriteria.DUMMY,
					Component.literal("Mob Conduit").withStyle(ChatFormatting.AQUA),
					ObjectiveCriteria.RenderType.INTEGER,
					false,
					null);
			scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
		}

		int conduits = 0;

		for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
			conduits += ConduitStore.get(level).conduits().size();
		}

		long attempts = SpawnStats.HOSTILE_NATURAL.get();
		long otherReason = SpawnStats.HOSTILE_OTHER_REASON.get();
		long suppressed = SpawnStats.SUPPRESSED.get();
		long outOfRange = SpawnStats.OUT_OF_RANGE.get();
		long noConduit = SpawnStats.SKIPPED_NO_ACTIVE.get();

		String[] rows = {
				ChatFormatting.GRAY + "non-natural hostiles: " + ChatFormatting.WHITE + otherReason,
				ChatFormatting.GRAY + "no active conduit: " + ChatFormatting.WHITE + noConduit,
				ChatFormatting.GRAY + "out of range: " + ChatFormatting.WHITE + outOfRange,
				ChatFormatting.GREEN + "suppressed: " + ChatFormatting.WHITE + suppressed,
				ChatFormatting.GRAY + "spawn attempts: " + ChatFormatting.WHITE + attempts,
				ChatFormatting.GOLD + "active conduits: " + ChatFormatting.WHITE + conduits,
		};

		if (!force && sameRows(rows)) {
			return;
		}

		// Row text is the score holder, so changing a number means a new row. Clear the old set
		// rather than leaving stale lines stacked in the sidebar.
		for (String old : lastRows) {
			scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(old), objective);
		}

		for (int i = 0; i < rows.length; i++) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(rows[i]), objective).set(i);
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
