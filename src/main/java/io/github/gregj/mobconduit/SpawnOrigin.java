package io.github.gregj.mobconduit;

import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Where a mob came from, read back off the entity itself.
 *
 * <p>Fabric records the {@link EntitySpawnReason} passed to
 * {@code EntityType.create(Level, EntitySpawnReason)} and exposes it through
 * {@link EntityLoadData}, which its mixin implements on {@code Entity}. That hook misses more
 * than it looks like: the reason overload delegates <em>into</em> the
 * {@code EntitySpawnRequest} overload, which Fabric does not hook, so everything routed through
 * {@code EntityType.loadEntityRecursive} — monster spawners, trial spawners, {@code /summon} —
 * records nothing, and constructor-built entities (vanilla's village siege) record nothing
 * either. {@code MobMixin} closes that gap by backfilling from {@code Mob.finalizeSpawn}, whose
 * {@code spawnReason} parameter is the true reason on every one of those paths
 * ({@code BaseSpawner.java:161}, {@code TrialSpawner.java:217}, {@code SummonCommand.java:90},
 * {@code VillageSiege.java:102}). The record is runtime-only — after a restart every entity
 * loaded from disk reads null.
 *
 * <p>{@code JOCKEY} is never a spawn of its own. Vanilla uses it for the companion mounted onto
 * another spawn during {@code finalizeSpawn}: the zombie horse's spear rider
 * ({@code ZombieHorse.java:132-146}) and the spider's skeleton ({@code Spider.java:152-158})
 * are riders, while the chicken under a baby zombie ({@code Zombie.java:471-478}) and the
 * zombie nautilus under a drowned are vehicles added separately. A jockey therefore inherits
 * the reason of whichever mob it arrived attached to — its vehicle if it is the rider, else its
 * first passenger if it is the mount.
 */
public final class SpawnOrigin {
	/**
	 * Spawn reasons the sweep leaves alone: the output of player-built machinery, which the
	 * design promises keeps working inside the radius — spawner and breeding farms and the
	 * conversion chains they feed (zombie-to-drowned trident farms).
	 *
	 * <p>Deliberately absent:
	 * <ul>
	 *   <li>{@code SPAWN_ITEM_USE}, {@code COMMAND}, {@code DISPENSER} — a spawn-egg or
	 *   {@code /summon} hostile spawns fine (suppression only vetoes natural spawns) but is
	 *   then fair game for the forcefield like any other hostile standing in the radius.</li>
	 *   <li>{@code TRIGGERED} — its vanilla uses are either already covered (trap horsemen
	 *   carry {@code setPersistenceRequired}, the wither and warden belong in
	 *   {@code removal_exempt_types}) or actively want sweeping ({@code removal_drops} killing
	 *   a cube mob splits it into TRIGGERED children, which would otherwise pile up
	 *   exempt).</li>
	 * </ul>
	 */
	private static final Set<EntitySpawnReason> SWEEP_EXEMPT = EnumSet.of(
			EntitySpawnReason.SPAWNER,
			EntitySpawnReason.TRIAL_SPAWNER,
			EntitySpawnReason.BREEDING,
			EntitySpawnReason.MOB_SUMMONED,
			EntitySpawnReason.BUCKET,
			EntitySpawnReason.CONVERSION);

	/**
	 * The non-{@code Enemy} mobs the conduit still treats as hostile spawns. 26.2 has exactly
	 * four MONSTER-category types that do not implement {@code Enemy}: these three undead
	 * mounts, which exist to carry a hostile rider in on their backs, and the sulfur cube,
	 * which is a passive, farmable resource mob and is deliberately not listed. An explicit
	 * set rather than a category check so the sulfur cube — and whatever passive
	 * MONSTER-category mob a future version adds — stays untouched.
	 */
	public static final Set<EntityType<?>> UNDEAD_MOUNTS = Set.of(
			EntityTypes.ZOMBIE_HORSE,
			EntityTypes.CAMEL_HUSK,
			EntityTypes.ZOMBIE_NAUTILUS);

	/**
	 * Reasons backfilled from {@code Mob.finalizeSpawn} for entities Fabric's create-hook
	 * missed. Weak keys so entries die with their entity; synchronized because chunk
	 * generation finalizes spawns off the server thread.
	 */
	private static final Map<Entity, EntitySpawnReason> BACKFILLED =
			Collections.synchronizedMap(new WeakHashMap<>());

	private SpawnOrigin() {
	}

	/** Called by {@code MobMixin} on every {@code finalizeSpawn}; first reason wins. */
	public static void backfill(Entity entity, EntitySpawnReason reason) {
		if (reason != null && recorded(entity) == null) {
			BACKFILLED.putIfAbsent(entity, reason);
		}
	}

	/** The reason recorded at creation, or null for disk-loaded and unhooked-path entities. */
	public static EntitySpawnReason recorded(Entity entity) {
		if (entity instanceof EntityLoadData data && data.spawnReason() != null) {
			return data.spawnReason();
		}

		return BACKFILLED.get(entity);
	}

	/** The recorded reason of the mob a JOCKEY entity arrived attached to; null when it has none. */
	public static EntitySpawnReason companionReason(Entity entity) {
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
	public static EntitySpawnReason effectiveReason(Entity entity) {
		EntitySpawnReason reason = recorded(entity);

		if (reason == EntitySpawnReason.JOCKEY) {
			EntitySpawnReason companion = companionReason(entity);
			return companion != null ? companion : reason;
		}

		return reason;
	}

	public static boolean sweepExempt(EntitySpawnReason reason) {
		return reason != null && SWEEP_EXEMPT.contains(reason);
	}
}
