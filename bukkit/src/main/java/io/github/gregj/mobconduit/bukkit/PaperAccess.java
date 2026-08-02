package io.github.gregj.mobconduit.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Paper-only API, isolated so the jar still links on plain Spigot.
 *
 * <p>{@link Entity#getEntitySpawnReason()} does not exist on Spigot. The method reference sits
 * in this class only, and the JVM resolves it lazily on first execution, so loading
 * {@code PaperAccess} is safe anywhere as long as {@link #spawnReason} is called solely behind
 * {@link #available()}. The same pattern keeps the Adventure action bar off Spigot, see
 * {@link ActionBars}.
 */
public final class PaperAccess {
	private static final boolean PAPER = detectPaper();

	private PaperAccess() {
	}

	private static boolean detectPaper() {
		try {
			Class.forName("io.papermc.paper.ServerBuildInfo");
			return true;
		} catch (ClassNotFoundException ignored) {
			// Fall through to the older check.
		}

		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	public static boolean available() {
		return PAPER;
	}

	/** Paper's own spawn-reason record; null for disk-loaded entities and unseen paths. */
	public static CreatureSpawnEvent.SpawnReason spawnReason(Entity entity) {
		return entity.getEntitySpawnReason();
	}
}
