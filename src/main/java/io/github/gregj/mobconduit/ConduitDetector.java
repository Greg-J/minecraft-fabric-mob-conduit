package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Turns an end crystal's tick into an activation decision.
 *
 * <p>Detection is event-driven: nothing ever scans the world looking for conduits. The crystal
 * is an entity, so there is no block entity of ours to tick, which is why this hangs off the
 * crystal's own tick via a Mixin.
 */
public final class ConduitDetector {
	/**
	 * Ticks between frame re-checks for a given crystal. Two seconds is far below any rate at
	 * which a player can build or break a frame and notice a delay.
	 */
	private static final int CHECK_INTERVAL_TICKS = 40;

	/**
	 * Last position each crystal was validated at, so a same-level move (a teleport fires no
	 * ENTITY_UNLOAD) deactivates the conduit it left behind instead of leaving a crystal-less
	 * suppression zone that persists forever. Weak keys: entries die with the crystal. Crystal
	 * ticks run on the server thread, so no synchronization.
	 */
	private static final java.util.WeakHashMap<EndCrystal, BlockPos> LAST_VALIDATED_AT = new java.util.WeakHashMap<>();

	private ConduitDetector() {
	}

	/**
	 * Called from the end crystal tick Mixin, so this runs for every crystal in the world on
	 * every tick and must stay cheap. The interval check is offset by entity id so crystals
	 * placed on the same tick do not all re-scan together.
	 */
	public static void onCrystalTick(EndCrystal crystal) {
		ModConfig config = ModConfig.get();
		int phase = crystal.time + crystal.getId();
		boolean due = phase % CHECK_INTERVAL_TICKS == 0;
		boolean ambientDue = phase % ConduitSounds.AMBIENT_INTERVAL_TICKS == 0;
		boolean forcefieldDue = config.forcefield() && phase % config.forcefieldIntervalTicks() == 0;
		boolean dripDue = config.frameDripsEnabled() && phase % config.frameDripIntervalTicks() == 0;
		boolean auraDue = config.crystalAuraEnabled() && phase % config.crystalAuraIntervalTicks() == 0;

		if (!due && !ambientDue && !forcefieldDue && !dripDue && !auraDue) {
			return;
		}

		if (!(crystal.level() instanceof ServerLevel level)) {
			return;
		}

		BlockPos pos = crystal.blockPosition();

		if (due) {
			BlockPos previous = LAST_VALIDATED_AT.put(crystal, pos);

			if (previous != null && !previous.equals(pos)) {
				ConduitStore.get(level).deactivate(level, previous);
			}

			validate(level, pos);
		}

		ConduitStore store = ConduitStore.get(level);

		if (!store.isActiveAt(pos)) {
			return;
		}

		if (ambientDue) {
			ConduitSounds.ambient(level, pos);
		}

		if (dripDue) {
			ConduitParticles.frameDrips(level, pos);
		}

		if (auraDue) {
			ConduitParticles.crystalAura(level, pos);
		}

		// Opt-in only. This is the radius scan the rest of the mod is built to avoid, so it runs
		// on an interval off the crystal's existing throttle rather than per tick.
		if (forcefieldDue) {
			store.sweepAt(level, pos);
		}
	}

	/** Re-reads the frame at {@code pos} and activates or deactivates accordingly. */
	public static void validate(ServerLevel level, BlockPos pos) {
		ModConfig config = ModConfig.get();
		ConduitStore store = ConduitStore.get(level);

		if (config.isDimensionDisabled(level.dimension().identifier())) {
			// Conduits do nothing in this dimension; make that visible immediately rather
			// than leaving an entry that suppresses nothing but confuses /mobconduit status.
			store.deactivate(level, pos);
			return;
		}

		int frameCount = FrameShape.count(level, pos, config.frameBlock());

		if (frameCount >= config.frameThresholdMin()) {
			store.activate(level, pos, frameCount);
		} else {
			store.deactivate(level, pos);
		}
	}
}
