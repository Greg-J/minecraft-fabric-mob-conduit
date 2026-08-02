package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * A floating status line above the crystal, built from a vanilla {@code text_display} entity —
 * the most "modded" thing a vanilla client can be shown without installing anything. The
 * display is a real entity, so it saves with the chunk. That is fine as long as exactly one
 * ever exists per conduit, which the tag-and-dedup below guarantees:
 *
 * <p>Every hologram carries the {@value #TAG} scoreboard tag, and {@link #show} removes any
 * tagged display at the position before spawning. The paths that could otherwise duplicate —
 * chunk unload/reload (deactivation cannot remove an unloading hologram, so the saved one
 * comes back) and server restart (holograms restore from disk) — both funnel through
 * activation, where the dedup collapses them back to one.
 */
public final class Holograms {
	private static final String TAG = "mobconduit.hologram";

	/** Matches the particle effects' TOP_OFFSET: above the frame's top ring at crystal +2. */
	private static final double HEIGHT_ABOVE_CRYSTAL = 3.0;

	private Holograms() {
	}

	/** Spawns the hologram, replacing any that already exists at this position. */
	public static void show(World world, Conduit conduit) {
		remove(world, conduit.pos());

		if (!ModConfig.get().hologram()) {
			return;
		}

		ConduitPos pos = conduit.pos();
		TextDisplay display = world.spawn(
				new Location(world, pos.centerX(), pos.y() + HEIGHT_ABOVE_CRYSTAL, pos.centerZ()),
				TextDisplay.class);
		display.setText(text(conduit));
		display.setBillboard(Display.Billboard.CENTER);
		display.setInvulnerable(true);
		display.setPersistent(true);
		display.addScoreboardTag(TAG);
	}

	/**
	 * Brings the hologram in line with the config and the conduit's derived radius. Called on
	 * revalidation, the one path where the radius can change without an activation.
	 */
	public static void refresh(World world, Conduit conduit) {
		TextDisplay existing = find(world, conduit.pos());

		if (!ModConfig.get().hologram()) {
			if (existing != null) {
				existing.remove();
			}

			return;
		}

		if (existing != null) {
			existing.setText(text(conduit));
		} else {
			show(world, conduit);
		}
	}

	/** Removes every hologram at this position. Safe on unloaded chunks. */
	public static void remove(World world, ConduitPos pos) {
		for (TextDisplay display : findAll(world, pos)) {
			display.remove();
		}
	}

	private static TextDisplay find(World world, ConduitPos crystalPos) {
		List<TextDisplay> found = findAll(world, crystalPos);
		return found.isEmpty() ? null : found.get(0);
	}

	private static List<TextDisplay> findAll(World world, ConduitPos crystalPos) {
		// getNearbyEntities never forces a chunk load, and finds nothing when unloaded —
		// which is correct, because the saved hologram is deduped on the next activation. The
		// box must reach the hologram's own height, HEIGHT_ABOVE_CRYSTAL above the crystal.
		List<TextDisplay> found = new ArrayList<>();

		for (org.bukkit.entity.Entity entity : world.getNearbyEntities(
				new Location(world, crystalPos.centerX(), crystalPos.centerY(), crystalPos.centerZ()),
				2.0, HEIGHT_ABOVE_CRYSTAL + 1.5, 2.0,
				entity -> entity instanceof TextDisplay && entity.getScoreboardTags().contains(TAG))) {
			found.add((TextDisplay) entity);
		}

		return found;
	}

	private static String text(Conduit conduit) {
		return "Mob Conduit\nradius " + conduit.radius()
				+ (conduit.cylindrical() ? " · cylinder" : "");
	}
}
