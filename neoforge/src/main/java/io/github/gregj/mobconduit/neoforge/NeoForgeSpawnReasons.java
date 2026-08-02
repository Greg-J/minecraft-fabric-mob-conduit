package io.github.gregj.mobconduit.neoforge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The NeoForge half of the spawn-reason record Fabric exposes as {@code EntityLoadData}.
 * {@code EntityTypeMixin} writes on entity creation, {@link NeoForgePlatform#spawnReason}
 * reads. Weak keys so entries die with their entity; synchronized because chunk generation
 * creates entities off the server thread — the same pattern {@code SpawnOrigin.BACKFILLED}
 * uses.
 */
public final class NeoForgeSpawnReasons {
	private static final Map<Entity, EntitySpawnReason> REASONS =
			Collections.synchronizedMap(new WeakHashMap<>());

	private NeoForgeSpawnReasons() {
	}

	/** The entity may be null: {@code create} refuses spawns by returning null. */
	public static void record(Entity entity, EntitySpawnReason reason) {
		if (entity != null && reason != null) {
			REASONS.put(entity, reason);
		}
	}

	/** The reason recorded at creation, or null for disk-loaded and unhooked-path entities. */
	public static EntitySpawnReason get(Entity entity) {
		return REASONS.get(entity);
	}
}
