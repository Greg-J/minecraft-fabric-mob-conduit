package io.github.gregj.mobconduit.bukkit.paper;

import io.github.gregj.mobconduit.bukkit.PaperAccess;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Paper-only operations. Lives in the paper source set because
 * {@link Entity#getEntitySpawnReason()} does not exist in spigot-api; loaded reflectively by
 * {@link PaperAccess} only when a Paper runtime is present.
 */
public final class PaperHooks implements PaperAccess.Hooks {
	@Override
	public SpawnReason spawnReason(Entity entity) {
		return entity.getEntitySpawnReason();
	}
}
