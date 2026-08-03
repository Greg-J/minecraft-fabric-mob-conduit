package io.github.gregj.mobconduit.bukkit.paper;

import io.github.gregj.mobconduit.bukkit.PaperAccess;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Paper-only operations. Lives in the paper source set because these members
 * ({@link Entity#getEntitySpawnReason()}, {@link Bukkit#getCommandMap()}) do not exist in
 * spigot-api; loaded reflectively by {@link PaperAccess} only when a Paper runtime is present.
 */
public final class PaperHooks implements PaperAccess.Hooks {
	@Override
	public SpawnReason spawnReason(Entity entity) {
		return entity.getEntitySpawnReason();
	}

	@Override
	public void registerCommand(String fallbackPrefix, Command command) {
		Bukkit.getCommandMap().register(fallbackPrefix, command);
	}
}
