package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;

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
	public static void show(ServerLevel level, Conduit conduit) {
		remove(level, conduit.pos());

		if (!ModConfig.get().hologram()) {
			return;
		}

		BlockPos pos = conduit.pos();
		Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		display.setPos(pos.getX() + 0.5, pos.getY() + HEIGHT_ABOVE_CRYSTAL, pos.getZ() + 0.5);
		display.setText(text(conduit));
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setInvulnerable(true);
		display.addTag(TAG);
		level.addFreshEntity(display);
	}

	/**
	 * Brings the hologram in line with the config and the conduit's derived radius. Called on
	 * revalidation, the one path where the radius can change without an activation.
	 */
	public static void refresh(ServerLevel level, Conduit conduit) {
		Display.TextDisplay existing = find(level, conduit.pos());

		if (!ModConfig.get().hologram()) {
			if (existing != null) {
				existing.remove(Entity.RemovalReason.DISCARDED);
			}

			return;
		}

		if (existing != null) {
			existing.setText(text(conduit));
		} else {
			show(level, conduit);
		}
	}

	/** Removes the hologram at this position if one exists. Safe on unloaded chunks. */
	public static void remove(ServerLevel level, BlockPos pos) {
		Display.TextDisplay existing = find(level, pos);

		if (existing != null) {
			existing.remove(Entity.RemovalReason.DISCARDED);
		}
	}

	private static Display.TextDisplay find(ServerLevel level, BlockPos crystalPos) {
		// Section-storage query: cannot force a chunk load, and finds nothing when unloaded —
		// which is correct, because the saved hologram is deduped on the next activation.
		for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class,
				new AABB(crystalPos).inflate(1.5), entity -> entity.entityTags().contains(TAG))) {
			return display;
		}

		return null;
	}

	private static Component text(Conduit conduit) {
		return Component.literal("Mob Conduit\nradius " + conduit.radius()
				+ (conduit.cylindrical() ? " · cylinder" : ""));
	}
}
