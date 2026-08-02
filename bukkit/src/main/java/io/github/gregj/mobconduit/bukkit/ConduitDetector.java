package io.github.gregj.mobconduit.bukkit;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turns an end crystal's continued existence into an activation decision.
 *
 * <p>Detection is event-driven: nothing ever scans the world looking for conduits. The
 * crystal is an entity, so there is no block entity of ours to tick. Fabric hooks the
 * crystal's own tick via a Mixin; Bukkit has no entity-tick hook, so crystals discovered
 * through {@code EntityPlaceEvent}, {@code EntitiesLoadEvent} and a one-time scan at enable
 * are kept in a tracked set that the scheduler walks every tick. Each crystal still
 * re-validates only every {@link #CHECK_INTERVAL_TICKS}, phased by entity id so crystals
 * placed on the same tick do not all re-scan together.
 */
public final class ConduitDetector {
	/**
	 * Ticks between frame re-checks for a given crystal. Two seconds is far below any rate at
	 * which a player can build or break a frame and notice a delay.
	 */
	private static final int CHECK_INTERVAL_TICKS = 40;

	/**
	 * Tracked crystals and the last position each was validated at, so a same-world move (a
	 * teleport fires no removal event) deactivates the conduit it left behind instead of
	 * leaving a crystal-less suppression zone that persists forever. Weak keys: entries die
	 * with the crystal. Everything runs on the server thread, so no synchronization.
	 */
	private static final Map<Entity, ConduitPos> LAST_VALIDATED_AT = new WeakHashMap<>();

	private ConduitDetector() {
	}

	/** Starts watching a crystal. Called from placement, chunk-load and the enable scan. */
	public static void track(EnderCrystal crystal) {
		LAST_VALIDATED_AT.putIfAbsent(crystal, null);
	}

	/** Drops every tracked crystal. Called on disable so nothing pins a dead world. */
	public static void clearAll() {
		LAST_VALIDATED_AT.clear();
	}

	/**
	 * Runs every tick from the scheduler, for every tracked crystal, and must stay cheap. The
	 * interval checks are offset by entity id so crystals placed on the same tick do not all
	 * re-scan together.
	 */
	public static void tick() {
		ModConfig config = ModConfig.get();
		Iterator<Map.Entry<Entity, ConduitPos>> iterator = LAST_VALIDATED_AT.entrySet().iterator();

		while (iterator.hasNext()) {
			Entity crystal = iterator.next().getKey();

			if (!crystal.isValid()) {
				// Killed, exploded or unloaded with its chunk. The removal event has already
				// parked any deactivation; this just keeps the tracked set small.
				iterator.remove();
				continue;
			}

			int phase = crystal.getTicksLived() + crystal.getEntityId();
			boolean due = phase % CHECK_INTERVAL_TICKS == 0;
			boolean ambientDue = phase % ConduitSounds.AMBIENT_INTERVAL_TICKS == 0;
			boolean forcefieldDue = config.forcefield() && phase % config.forcefieldIntervalTicks() == 0;
			boolean dripDue = config.frameDripsEnabled() && phase % config.frameDripIntervalTicks() == 0;
			boolean auraDue = config.crystalAuraEnabled() && phase % config.crystalAuraIntervalTicks() == 0;

			if (!due && !ambientDue && !forcefieldDue && !dripDue && !auraDue) {
				continue;
			}

			World world = crystal.getWorld();
			ConduitPos pos = ConduitPos.of(crystal);

			if (due) {
				ConduitPos previous = LAST_VALIDATED_AT.put(crystal, pos);

				if (previous != null && !previous.equals(pos)) {
					ConduitStore.get(world).deactivate(world, previous);
				}

				validate(world, pos);
			}

			ConduitStore store = ConduitStore.get(world);

			if (!store.isActiveAt(pos)) {
				continue;
			}

			if (ambientDue) {
				ConduitSounds.ambient(world, pos);
			}

			if (dripDue) {
				ConduitParticles.frameDrips(world, pos);
			}

			if (auraDue) {
				ConduitParticles.crystalAura(world, pos);
			}

			// Opt-in only. This is the radius scan the rest of the mod is built to avoid, so
			// it runs on an interval off the crystal's existing throttle rather than per tick.
			if (forcefieldDue) {
				store.sweepAt(world, pos);
			}
		}
	}

	/** Re-reads the frame at {@code pos} and activates or deactivates accordingly. */
	public static void validate(World world, ConduitPos pos) {
		ModConfig config = ModConfig.get();
		ConduitStore store = ConduitStore.get(world);

		if (config.isDimensionDisabled(world)) {
			// Conduits do nothing in this dimension; make that visible immediately rather
			// than leaving an entry that suppresses nothing but confuses /mobconduit status.
			store.deactivate(world, pos);
			return;
		}

		int frameCount = FrameShape.count(world, pos, config.frameBlock());

		if (frameCount >= config.frameThresholdMin()) {
			store.activate(world, pos, frameCount);
		} else {
			store.deactivate(world, pos);
		}
	}
}
