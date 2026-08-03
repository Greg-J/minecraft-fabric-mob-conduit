package io.github.gregj.mobconduit.bukkit;

import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Every Bukkit event the plugin listens to. The spawn guard is the hot one: it runs for every
 * creature spawn on the server, so the ordering of its checks is deliberate — cheapest and
 * most selective first.
 */
public final class MobConduitListener implements Listener {
	/**
	 * Spawn suppression. {@link CreatureSpawnEvent} is the Bukkit counterpart of the Fabric
	 * mod's {@code ALLOW_LOAD} veto, and carries a richer reason set than vanilla's
	 * {@code EntitySpawnReason}: raids, patrols, sieges, reinforcements and portal piglins
	 * arrive with explicit reasons here, so they keep spawning inside the radius by design —
	 * the leaks the Fabric platform cannot close, this one can.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		LivingEntity entity = event.getEntity();
		CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
		SpawnOrigins.record(entity, reason);

		World world = event.getLocation().getWorld();
		ModConfig config = ModConfig.get();

		if (world == null || config.isDimensionDisabled(world)) {
			return;
		}

		// Natural spawns only. Spawners, trial spawners, spawn eggs, breeding, commands and
		// every other reason keep working inside the radius. JOCKEY is the one indirect case:
		// vanilla mounts a companion onto a spawn during the vehicle's finalizeSpawn — the
		// zombie horse's spear rider, the spider's skeleton — so a jockey counts as natural
		// exactly when the mob it arrived attached to does. CHUNK_GEN is compared by name:
		// Paper has it deprecated for removal, and a hard enum reference would break the guard
		// outright on a build that drops the constant.
		CreatureSpawnEvent.SpawnReason effective = reason == CreatureSpawnEvent.SpawnReason.JOCKEY
				? SpawnOrigins.companionReason(entity)
				: reason;
		boolean natural = effective == CreatureSpawnEvent.SpawnReason.NATURAL
				|| (effective != null && "CHUNK_GEN".equals(effective.name()));

		if (!natural) {
			// The one non-natural spawn the conduit vetoes: a thunderstorm trap skeleton
			// horse. It exists solely to ambush — approach it and SkeletonTrapGoal spawns
			// four persistent enchanted-bow horsemen, TRIGGERED and setPersistenceRequired,
			// beyond both this filter and the sweep's exemptions. The trap flag is set before
			// the add, so refusing the trap here is the one clean interception point.
			if (entity instanceof SkeletonHorse horse && horse.isTrapped() && ConduitStore.anyActive()) {
				Conduit conduit = ConduitStore.get(world).suppressingConduit(ConduitPos.of(entity));

				if (conduit != null) {
					SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
					SpawnStats.SUPPRESSED.incrementAndGet();
					SuppressionFeedback.onVeto(world, entity, conduit);
					vetoSpawn(event, entity);
					return;
				}
			}

			// Everything else through here is deliberately allowed, but counted: without
			// this the sidebar reads near-100% suppression while an unconsidered spawn path
			// walks hostiles straight in.
			if (Hostiles.isSuppressible(entity)) {
				SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
			}

			return;
		}

		if (!Hostiles.isSuppressible(entity) || config.isExemptFromSuppression(entity.getType())) {
			return;
		}

		SpawnStats.HOSTILE_NATURAL.incrementAndGet();

		if (!ConduitStore.anyActive()) {
			SpawnStats.SKIPPED_NO_ACTIVE.incrementAndGet();
			return;
		}

		Conduit conduit = ConduitStore.get(world).suppressingConduit(ConduitPos.of(entity));

		if (conduit != null) {
			SpawnStats.SUPPRESSED.incrementAndGet();
			SuppressionFeedback.onVeto(world, entity, conduit);
			vetoSpawn(event, entity);
			return;
		}

		SpawnStats.OUT_OF_RANGE.incrementAndGet();
	}

	/**
	 * Refuses a spawn, severing any ride first. A vetoed spawn is a silent non-add — the
	 * entity object stays intact — and vanilla mounts jockey companions before anything is
	 * added. Left linked, the half that was allowed keeps a phantom passenger reference.
	 */
	private static void vetoSpawn(CreatureSpawnEvent event, Entity entity) {
		if (entity.isInsideVehicle()) {
			entity.leaveVehicle();
		}

		event.setCancelled(true);
	}

	/**
	 * Deactivation hangs off entity removal rather than a block break, which covers a player
	 * killing the crystal, the crystal exploding, and the chunk unloading in one place.
	 *
	 * <p>Deferred, not immediate: deactivation touches blocks, and this event can fire from
	 * inside the chunk system's own update pass. The position is parked on the store and
	 * picked up by the scheduler at the end of the tick.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityRemove(EntityRemoveEvent event) {
		if (event.getEntity() instanceof EnderCrystal crystal) {
			World world = crystal.getWorld();
			ConduitStore store = ConduitStore.get(world);
			ConduitPos pos = ConduitPos.of(crystal);

			// Only park positions that are actually conduits: this fires for every end
			// crystal in the world, and the tick hook that drains the list is gated on a
			// conduit existing.
			if (store.isActiveAt(pos)) {
				store.deferDeactivate(pos);
			}
		}
	}

	/** A freshly placed crystal enters the tracked set; the poll validates it within 2s. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityPlace(EntityPlaceEvent event) {
		if (event.getEntity() instanceof EnderCrystal crystal) {
			ConduitDetector.track(crystal);
		}
	}

	/** Crystals loading with their chunk re-enter the tracked set for rediscovery. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntitiesLoad(EntitiesLoadEvent event) {
		for (Entity entity : event.getEntities()) {
			if (entity instanceof EnderCrystal crystal) {
				ConduitDetector.track(crystal);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldUnload(WorldUnloadEvent event) {
		ConduitStore.onWorldUnload(event.getWorld());
		RadiusVisualizer.clearWorld(event.getWorld());
	}
}
