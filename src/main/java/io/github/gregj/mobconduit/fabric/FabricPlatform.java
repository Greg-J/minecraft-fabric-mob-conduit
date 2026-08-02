package io.github.gregj.mobconduit.fabric;

import com.mojang.brigadier.CommandDispatcher;
import io.github.gregj.mobconduit.Platform;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Fabric API wiring for the mod's platform needs. */
public final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public void registerSpawnGuard(SpawnGuard guard) {
		ServerEntityEvents.ALLOW_LOAD.register(guard::allow);
	}

	@Override
	public void registerEntityUnload(BiConsumer<Entity, ServerLevel> handler) {
		ServerEntityEvents.ENTITY_UNLOAD.register(handler::accept);
	}

	@Override
	public void registerEndLevelTick(Consumer<ServerLevel> handler) {
		ServerTickEvents.END_LEVEL_TICK.register(handler::accept);
	}

	@Override
	public void registerEndServerTick(Consumer<MinecraftServer> handler) {
		ServerTickEvents.END_SERVER_TICK.register(handler::accept);
	}

	@Override
	public void registerServerStarted(Consumer<MinecraftServer> handler) {
		ServerLifecycleEvents.SERVER_STARTED.register(handler::accept);
	}

	@Override
	public void registerServerStopping(Consumer<MinecraftServer> handler) {
		ServerLifecycleEvents.SERVER_STOPPING.register(handler::accept);
	}

	@Override
	public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				handler.accept(dispatcher));
	}

	/**
	 * Fabric records the reason passed to {@code EntityType.create(Level, EntitySpawnReason)}
	 * and exposes it through {@link EntityLoadData}. It misses the {@code EntitySpawnRequest}
	 * overload (spawners, trial spawners, {@code /summon}) and constructor-built entities;
	 * {@code SpawnOrigin}'s backfill covers those.
	 */
	@Override
	public EntitySpawnReason spawnReason(Entity entity) {
		return entity instanceof EntityLoadData data ? data.spawnReason() : null;
	}
}
