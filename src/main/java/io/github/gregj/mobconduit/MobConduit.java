package io.github.gregj.mobconduit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
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
		// every other reason keep working inside the radius.
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}

		if (!(entity instanceof Enemy)) {
			return true;
		}

		SpawnStats.HOSTILE_NATURAL.incrementAndGet();

		if (!ConduitStore.anyActive()) {
			SpawnStats.SKIPPED_NO_ACTIVE.incrementAndGet();
			return true;
		}

		if (ConduitStore.get(level).suppresses(entity.blockPosition())) {
			SpawnStats.SUPPRESSED.incrementAndGet();
			return false;
		}

		SpawnStats.OUT_OF_RANGE.incrementAndGet();
		return true;
	}

	/**
	 * Deactivation hangs off entity removal rather than a block break, which covers a player
	 * killing the crystal, the crystal exploding, and the chunk unloading in one place.
	 */
	private static void onEntityUnload(Entity entity, ServerLevel level) {
		if (entity instanceof EndCrystal) {
			ConduitStore.get(level).deactivate(level, entity.blockPosition());
		}
	}

	/**
	 * Also runs while effects are still in flight, not just while a conduit is active, so a
	 * conduit destroyed mid-erasure still gets its light blocks faded out and cleared.
	 */
	private static void onLevelTick(ServerLevel level) {
		if (ConduitStore.anyActive() || ConduitStore.anyPendingEffects()) {
			ConduitStore.get(level).drainRemovals(level);
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

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
