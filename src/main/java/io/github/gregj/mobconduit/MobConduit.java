package io.github.gregj.mobconduit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobConduit implements ModInitializer {
	public static final String MOD_ID = "mob-conduit";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig.load();

		ServerEntityEvents.ALLOW_LOAD.register(MobConduit::allowSpawn);
		ServerEntityEvents.ENTITY_UNLOAD.register(MobConduit::onEntityUnload);
		ServerTickEvents.END_LEVEL_TICK.register(MobConduit::onLevelTick);
		ServerTickEvents.END_SERVER_TICK.register(StatusBoard::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(MobConduit::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(MobConduit::onServerStopping);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				MobConduitCommand.register(dispatcher));
	}

	/**
	 * Spawn suppression. Fabric API 0.156.0+26.2 has no spawn-specific event, but
	 * {@code ServerEntityEvents.ALLOW_LOAD} carries the spawn reason and can veto the load,
	 * which covers this without a Mixin.
	 *
	 * <p>This runs for every entity entering every level, so the ordering of these checks is
	 * deliberate: cheapest and most selective first.
	 */
	private static boolean allowSpawn(Entity entity, ServerLevel level, EntitySpawnReason reason, boolean loadedFromDisk) {
		if (loadedFromDisk) {
			return true;
		}

		// Natural spawns only. Spawners, trial spawners, spawn eggs, breeding, commands and
		// every other reason keep working inside the radius. JOCKEY is the one indirect case:
		// vanilla mounts a companion onto a spawn during the vehicle's finalizeSpawn — the
		// zombie horse's spear rider, the spider's skeleton — so a jockey counts as natural
		// exactly when the mob it arrived attached to does.
		EntitySpawnReason effective = reason == EntitySpawnReason.JOCKEY
				? SpawnOrigin.companionReason(entity)
				: reason;

		if (effective != EntitySpawnReason.NATURAL && effective != EntitySpawnReason.CHUNK_GENERATION) {
			// The one non-natural spawn the conduit vetoes: a thunderstorm trap skeleton horse.
			// It exists solely to ambush — approach it and SkeletonTrapGoal spawns four
			// persistent enchanted-bow horsemen, TRIGGERED and setPersistenceRequired, beyond
			// both this filter and the sweep's exemptions. The trap flag is set before the add
			// (ServerLevel.tickThunder, ServerLevel.java:556-562), so refusing the trap here is
			// the one clean interception point.
			if (entity instanceof SkeletonHorse horse && horse.isTrap()
					&& ConduitStore.anyActive()
					&& ConduitStore.get(level).suppresses(entity.blockPosition())) {
				SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
				SpawnStats.SUPPRESSED.incrementAndGet();
				return vetoSpawn(entity);
			}

			// Everything else through here is deliberately allowed, but counted: without this
			// the sidebar reads near-100% suppression while an unconsidered vanilla spawn path
			// walks hostiles straight in, which is exactly how the jockey leak stayed invisible.
			if (Hostiles.isSuppressible(entity)) {
				SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
			}

			return true;
		}

		if (!Hostiles.isSuppressible(entity)) {
			return true;
		}

		SpawnStats.HOSTILE_NATURAL.incrementAndGet();

		if (!ConduitStore.anyActive()) {
			SpawnStats.SKIPPED_NO_ACTIVE.incrementAndGet();
			return true;
		}

		if (ConduitStore.get(level).suppresses(entity.blockPosition())) {
			SpawnStats.SUPPRESSED.incrementAndGet();
			return vetoSpawn(entity);
		}

		SpawnStats.OUT_OF_RANGE.incrementAndGet();
		return true;
	}

	/**
	 * Refuses a spawn, severing any ride first. A vetoed load is a silent non-add — the entity
	 * object stays intact — and vanilla mounts jockey companions before anything is added. Left
	 * linked, the half that was allowed keeps a phantom passenger reference, serializes it under
	 * {@code Passengers} on chunk save, and the vetoed mob materializes on the next chunk load
	 * through the loaded-from-disk allowance above. Vehicles are processed before their
	 * passengers ({@code ServerLevelAccessor.addFreshEntityWithPassengers}), so by the time a
	 * mounted pair is fully evaluated every refused rider has unlinked itself.
	 */
	private static boolean vetoSpawn(Entity entity) {
		if (entity.isPassenger()) {
			entity.stopRiding();
		}

		return false;
	}

	/**
	 * Deactivation hangs off entity removal rather than a block break, which covers a player
	 * killing the crystal, the crystal exploding, and the chunk unloading in one place.
	 *
	 * <p>Deferred, not immediate: this event fires inside the chunk system's own update pass —
	 * a ticket-level change is what unloads the crystal's section — and deactivation touches
	 * blocks. Calling setBlock from here re-enters the chunk system mid-iteration and crashes
	 * the server (NPE in {@code DistanceManager.runAllUpdates};
	 * {@code crash-2026-07-29_18.15.39}). Vanilla's task queue cannot defer this either, since
	 * {@code BlockableEventLoop.execute} runs inline when already on the server thread
	 * ({@code BlockableEventLoop.java:49-51,98-105}), so the position is parked on the store
	 * and picked up by {@link #onLevelTick} at the end of the tick.
	 */
	private static void onEntityUnload(Entity entity, ServerLevel level) {
		if (entity instanceof EndCrystal) {
			ConduitStore store = ConduitStore.get(level);
			BlockPos pos = entity.blockPosition();

			// Only park positions that are actually conduits: this fires for every end
			// crystal in the world, and the tick hook that drains the list is gated on a
			// conduit existing.
			if (store.isActiveAt(pos)) {
				store.deferDeactivate(pos);
			}
		}
	}

	/**
	 * Also runs while effects are still in flight, not just while a conduit is active, so a
	 * conduit destroyed mid-erasure still gets its light blocks faded out and cleared.
	 */
	private static void onLevelTick(ServerLevel level) {
		if (RadiusVisualizer.isActive()) {
			RadiusVisualizer.tick(level);
		}

		if (ConduitStore.anyActive() || ConduitStore.anyPendingEffects()) {
			ConduitStore store = ConduitStore.get(level);
			store.drainDeactivations(level);
			store.drainRemovals(level);
		}
	}

	private static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		ModConfig config = ModConfig.get();
		int simulationBlocks = server.getPlayerList().getSimulationDistance() * 16;

		if (config.radiusMax() > simulationBlocks) {
			LOGGER.warn(
					"radius_max is {} blocks but simulation distance is only {} chunks ({} blocks). "
							+ "Beyond that the extra radius does nothing, because unticked chunks do not spawn mobs.",
					config.radiusMax(), server.getPlayerList().getSimulationDistance(), simulationBlocks);
		}

		LOGGER.info("Frame block {}, activates at {} frame blocks (radius {}), full frame {} (radius {})",
				config.frameBlockName(), config.frameThresholdMin(), config.radiusFor(config.frameThresholdMin()),
				config.frameThresholdMax(), config.radiusMax());
	}

	private static void onServerStopping(net.minecraft.server.MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			ConduitStore.get(level).forget(level);
		}
	}
}
