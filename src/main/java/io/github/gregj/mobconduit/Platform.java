package io.github.gregj.mobconduit;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Everything the mod needs from the loader it runs on. The mod's logic is loader-neutral
 * vanilla code; the entrypoint of each platform (Fabric, NeoForge) supplies an implementation
 * and calls {@code MobConduit.init(platform)}.
 *
 * <p>Kept deliberately small: event registration, the config directory, and the spawn-reason
 * lookup. Anything that can be expressed against vanilla classes stays out of here.
 */
public interface Platform {
	Path configDir();

	/** Registers the spawn veto. The guard returns false to refuse the entity add. */
	void registerSpawnGuard(SpawnGuard guard);

	/** Fires when an entity leaves a level: killed, exploded, or unloaded with its chunk. */
	void registerEntityUnload(BiConsumer<Entity, ServerLevel> handler);

	void registerEndLevelTick(Consumer<ServerLevel> handler);

	void registerEndServerTick(Consumer<MinecraftServer> handler);

	void registerServerStarted(Consumer<MinecraftServer> handler);

	void registerServerStopping(Consumer<MinecraftServer> handler);

	void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler);

	/**
	 * The reason the platform recorded for this entity's current add, or null for disk-loaded
	 * entities and paths the platform's capture misses (spawners, trial spawners,
	 * {@code /summon} — {@code SpawnOrigin}'s backfill covers those).
	 */
	EntitySpawnReason spawnReason(Entity entity);

	@FunctionalInterface
	interface SpawnGuard {
		/** Return false to veto. {@code reason} may be null on paths the platform cannot see. */
		boolean allow(Entity entity, ServerLevel level, EntitySpawnReason reason, boolean loadedFromDisk);
	}
}
