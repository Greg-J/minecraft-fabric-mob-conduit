package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Tells players in range that the conduit just ate a spawn. This is the category's most
 * requested feature: without it "is it working?" is unanswerable without watching the
 * scoreboard. Rate-limited per conduit, because spawn attempts fire constantly.
 */
public final class SuppressionFeedback {
	private static final int COOLDOWN_TICKS = 40;

	private SuppressionFeedback() {
	}

	public static void onVeto(World world, Entity entity, Conduit conduit) {
		ModConfig.FeedbackMode mode = ModConfig.get().suppressionFeedback();

		if (mode == ModConfig.FeedbackMode.OFF) {
			return;
		}

		if (!ConduitStore.get(world).markFeedback(conduit.pos(), world.getGameTime(), COOLDOWN_TICKS)) {
			return;
		}

		if (mode == ModConfig.FeedbackMode.PARTICLE) {
			world.spawnParticle(Particle.SOUL_FIRE_FLAME,
					entity.getLocation().getX(), entity.getLocation().getY() + 0.5, entity.getLocation().getZ(),
					3, 0.3, 0.4, 0.3, 0.02, null, false);
			return;
		}

		String message = "Mob Conduit suppressed a " + entity.getType().getName();

		for (Player player : world.getPlayers()) {
			if (conduit.covers(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
				ActionBars.send(player, message);
			}
		}
	}
}
