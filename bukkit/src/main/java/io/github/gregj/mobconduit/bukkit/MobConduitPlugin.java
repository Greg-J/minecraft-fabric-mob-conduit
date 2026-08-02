package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Bukkit entrypoint. Pure {@code org.bukkit.*} — no NMS, no mixins. Paper-only API is reached
 * only through classes loaded behind a runtime Paper check ({@link PaperAccess},
 * {@link ActionBars}), so this jar also loads on plain Spigot.
 *
 * <p>The per-tick pipeline mirrors the Fabric mod's end-of-level tick: drain parked
 * deactivations and the erasure pipeline for each world, tick the radius visualizer, walk the
 * tracked crystals, and refresh the sidebar.
 */
public final class MobConduitPlugin extends JavaPlugin {
	private static MobConduitPlugin instance;

	public MobConduitPlugin() {
		instance = this;
	}

	public static MobConduitPlugin instance() {
		return instance;
	}

	public static Logger logger() {
		return instance.getLogger();
	}

	@Override
	public void onEnable() {
		ModConfig.load();
		ActionBars.detect();

		Bukkit.getPluginManager().registerEvents(new MobConduitListener(), this);
		new MobConduitCommand(this).register();

		// Load persisted conduits, then pick up every already-loaded crystal. Crystals in
		// chunks that load later arrive through EntitiesLoadEvent.
		for (World world : Bukkit.getWorlds()) {
			ConduitStore.get(world);

			for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
				ConduitDetector.track(crystal);
			}
		}

		Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

		warnIfRadiusExceedsSimulationDistance();

		ModConfig config = ModConfig.get();
		getLogger().info("Frame block " + config.frameBlockName()
				+ ", activates at " + config.frameThresholdMin() + " frame blocks (radius "
				+ config.radiusFor(config.frameThresholdMin()) + "), full frame "
				+ config.frameThresholdMax() + " (radius " + config.radiusMax() + ")");
	}

	@Override
	public void onDisable() {
		RadiusVisualizer.clearAll();
		StatusBoard.disable();
		ConduitDetector.clearAll();
		// Saves every world, restores swapped base blocks and clears in-flight light blocks,
		// so a stop mid-fade leaves nothing behind.
		ConduitStore.forgetAll();
	}

	private void tick() {
		ConduitDetector.tick();

		for (World world : Bukkit.getWorlds()) {
			if (RadiusVisualizer.isActive()) {
				RadiusVisualizer.tick(world);
			}

			// Also runs while effects are still in flight, not just while a conduit is
			// active, so a conduit destroyed mid-erasure still gets its light blocks faded
			// out and cleared.
			if (ConduitStore.anyActive() || ConduitStore.anyPendingEffects()) {
				ConduitStore store = ConduitStore.get(world);
				store.drainDeactivations(world);
				store.drainRemovals(world);
			}
		}

		StatusBoard.tick();
	}

	private void warnIfRadiusExceedsSimulationDistance() {
		ModConfig config = ModConfig.get();

		for (World world : Bukkit.getWorlds()) {
			int simulationBlocks = world.getSimulationDistance() * 16;

			if (config.radiusMax() > simulationBlocks) {
				getLogger().warning("radius_max is " + config.radiusMax()
						+ " blocks but simulation distance in " + world.getName() + " is only "
						+ world.getSimulationDistance() + " chunks (" + simulationBlocks
						+ " blocks). Beyond that the extra radius does nothing, because unticked"
						+ " chunks do not spawn mobs.");
			}
		}
	}
}
