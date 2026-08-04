package io.github.gregj.mobconduit.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Paper-only API access, isolated so the jar both links on plain Spigot AND compiles without
 * the Paper API on the classpath.
 *
 * <p>Two mechanisms, two layers of safety. {@link #available()} is a pure reflection check,
 * safe anywhere. Everything Paper-specific lives in the {@code paper} source set (compiled
 * against paper-api): {@code PaperHooks} reads Paper's entity-stamped spawn reason, and the
 * action-bar sender in {@code ActionBars} comes from there too. Both are loaded by name, so
 * the main sources never reference Paper-only members — the build proves it, because main
 * compiles against spigot-api alone.
 */
public final class PaperAccess {
	/** Paper-only operations; implemented in the paper source set, loaded by name. */
	public interface Hooks {
		SpawnReason spawnReason(Entity entity);
	}

	private static final boolean PAPER = detectPaper();
	private static final Hooks HOOKS = loadHooks();

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

	private static Hooks loadHooks() {
		if (!PAPER) {
			return null;
		}

		try {
			return (Hooks) Class.forName("io.github.gregj.mobconduit.bukkit.paper.PaperHooks")
					.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	public static boolean available() {
		return PAPER;
	}

	/** Paper's own spawn-reason record; null off Paper, or for disk-loaded and unseen paths. */
	public static SpawnReason spawnReason(Entity entity) {
		return HOOKS != null ? HOOKS.spawnReason(entity) : null;
	}
}
