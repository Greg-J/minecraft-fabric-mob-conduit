package io.github.gregj.mobconduit.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import io.github.gregj.mobconduit.Platform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * NeoForge wiring for the mod's platform needs. Every hook is a listener on the game-wide
 * event bus; all of them fire on both sides, so anything level-based is gated to
 * {@link ServerLevel}, matching the server-only Fabric events in {@code FabricPlatform}.
 */
public final class NeoForgePlatform implements Platform {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	/**
	 * {@code EntityJoinLevelEvent} is the same shape as Fabric's {@code ALLOW_LOAD}: cancellable
	 * and carries whether the entity came from disk. The reason argument is our own capture —
	 * see {@code EntityTypeMixin} — looked up for the joining entity, null on a miss.
	 */
	@Override
	public void registerSpawnGuard(SpawnGuard guard) {
		NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level
					&& !guard.allow(event.getEntity(), level, spawnReason(event.getEntity()), event.loadedFromDisk())) {
				event.setCanceled(true);
			}
		});
	}

	@Override
	public void registerEntityUnload(BiConsumer<Entity, ServerLevel> handler) {
		NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				handler.accept(event.getEntity(), level);
			}
		});
	}

	@Override
	public void registerEndLevelTick(Consumer<ServerLevel> handler) {
		NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				handler.accept(level);
			}
		});
	}

	@Override
	public void registerEndServerTick(Consumer<MinecraftServer> handler) {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> handler.accept(event.getServer()));
	}

	@Override
	public void registerServerStarted(Consumer<MinecraftServer> handler) {
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> handler.accept(event.getServer()));
	}

	@Override
	public void registerServerStopping(Consumer<MinecraftServer> handler) {
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> handler.accept(event.getServer()));
	}

	@Override
	public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> handler.accept(event.getDispatcher()));
	}

	/**
	 * NeoForge has no equivalent of Fabric's {@code EntityLoadData}, so the reason is captured
	 * by {@code EntityTypeMixin} on {@code EntityType.create(Level, EntitySpawnReason)} and
	 * stored in {@link NeoForgeSpawnReasons}. It misses the same paths Fabric's hook does, by
	 * design; {@code SpawnOrigin}'s backfill covers those.
	 */
	@Override
	public EntitySpawnReason spawnReason(Entity entity) {
		return NeoForgeSpawnReasons.get(entity);
	}
}
