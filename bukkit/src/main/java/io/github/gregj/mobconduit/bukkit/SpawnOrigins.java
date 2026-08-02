package io.github.gregj.mobconduit.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Where a mob came from, read back off the entity itself. The Bukkit mirror of the Fabric
 * mod's {@code SpawnOrigin}.
 *
 * <p>{@link CreatureSpawnEvent} carries a reason for every spawn that goes through the event,
 * and {@link MobConduitListener} records it here. On Paper the entity also remembers its own
 * reason ({@code Entity#getEntitySpawnReason()}), which covers paths that never fire the
 * event; that record is read first where it exists. The record is runtime-only — after a
 * restart every entity loaded from disk reads null, and null means "fair game" to the sweep,
 * same as on Fabric.
 *
 * <p>Reason mapping from vanilla's {@code EntitySpawnReason} to Bukkit's: the sweep-exempt set
 * is {@code SPAWNER}, {@code TRIAL_SPAWNER}, {@code BREEDING}, {@code MOB_SUMMONED},
 * {@code BUCKET} and {@code CONVERSION}. Bukkit spells the conversion reasons out
 * individually ({@code INFECTION}, {@code CURED}, {@code DROWNED}, {@code PIGLIN_ZOMBIFIED},
 * {@code FROZEN}, {@code METAMORPHOSIS}), and vanilla's {@code MOB_SUMMONED} — evokers calling
 * vexes — arrives as {@code SPELL} here.
 *
 * <p>{@code JOCKEY} is never a spawn of its own: vanilla mounts a companion onto another spawn
 * during the vehicle's {@code finalizeSpawn}, so a jockey counts as natural exactly when the
 * mob it arrived attached to does.
 */
public final class SpawnOrigins {
	/**
	 * Spawn reasons the sweep leaves alone: the output of player-built machinery, which the
	 * design promises keeps working inside the radius — spawner and breeding farms and the
	 * conversion chains they feed (zombie-to-drowned trident farms).
	 *
	 * <p>Deliberately absent, mirroring the Fabric rules:
	 * <ul>
	 *   <li>{@code SPAWNER_EGG}, {@code COMMAND}, {@code DISPENSE_EGG} — a spawn-egg or
	 *   {@code /summon} hostile spawns fine (suppression only vetoes natural spawns) but is
	 *   then fair game for the forcefield like any other hostile standing in the radius.</li>
	 *   <li>{@code SLIME_SPLIT} — vanilla files split children as {@code TRIGGERED}, which the
	 *   Fabric rules leave out for the same reason: with {@code removal_drops} on, killing a
	 *   cube mob splits it into children that would otherwise pile up exempt.</li>
	 * </ul>
	 */
	private static final Set<SpawnReason> SWEEP_EXEMPT = EnumSet.of(
			SpawnReason.SPAWNER,
			SpawnReason.TRIAL_SPAWNER,
			SpawnReason.BREEDING,
			SpawnReason.SPELL,
			SpawnReason.BUCKET,
			SpawnReason.INFECTION,
			SpawnReason.CURED,
			SpawnReason.DROWNED,
			SpawnReason.PIGLIN_ZOMBIFIED,
			SpawnReason.FROZEN,
			SpawnReason.METAMORPHOSIS);

	/**
	 * Reasons recorded from {@link CreatureSpawnEvent}. Weak keys so entries die with their
	 * entity; synchronized because chunk generation can finalize spawns off the server thread.
	 */
	private static final Map<Entity, SpawnReason> RECORDED =
			Collections.synchronizedMap(new WeakHashMap<>());

	private SpawnOrigins() {
	}

	/** Called by the spawn listener on every {@link CreatureSpawnEvent}; first reason wins. */
	public static void record(Entity entity, SpawnReason reason) {
		if (reason != null) {
			RECORDED.putIfAbsent(entity, reason);
		}
	}

	/** The reason recorded at spawn, or null for disk-loaded and unseen-path entities. */
	public static SpawnReason recorded(Entity entity) {
		if (PaperAccess.available()) {
			SpawnReason reason = PaperAccess.spawnReason(entity);

			if (reason != null) {
				return reason;
			}
		}

		return RECORDED.get(entity);
	}

	/** The recorded reason of the mob a JOCKEY entity arrived attached to; null when it has none. */
	public static SpawnReason companionReason(Entity entity) {
		Entity vehicle = entity.getVehicle();

		if (vehicle != null) {
			return recorded(vehicle);
		}

		for (Entity passenger : entity.getPassengers()) {
			return recorded(passenger);
		}

		return null;
	}

	/** Recorded reason, with JOCKEY resolved to the companion's when one is attached. */
	public static SpawnReason effectiveReason(Entity entity) {
		SpawnReason reason = recorded(entity);

		if (reason == SpawnReason.JOCKEY) {
			SpawnReason companion = companionReason(entity);
			return companion != null ? companion : reason;
		}

		return reason;
	}

	public static boolean sweepExempt(SpawnReason reason) {
		return reason != null && SWEEP_EXEMPT.contains(reason);
	}
}
