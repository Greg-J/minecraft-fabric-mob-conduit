package io.github.gregj.mobconduit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Tells players in range that the conduit just ate a spawn. This is the category's most
 * requested feature: without it "is it working?" is unanswerable without watching the
 * scoreboard. Rate-limited per conduit, because spawn attempts fire constantly.
 */
public final class SuppressionFeedback {
	private static final int COOLDOWN_TICKS = 40;

	private SuppressionFeedback() {
	}

	public static void onVeto(ServerLevel level, Entity entity, Conduit conduit) {
		ModConfig.FeedbackMode mode = ModConfig.get().suppressionFeedback();

		if (mode == ModConfig.FeedbackMode.OFF) {
			return;
		}

		if (!ConduitStore.get(level).markFeedback(conduit.pos(), level.getGameTime(), COOLDOWN_TICKS)) {
			return;
		}

		if (mode == ModConfig.FeedbackMode.PARTICLE) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					entity.getX(), entity.getY() + 0.5, entity.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			return;
		}

		Component message = Component.literal("Mob Conduit suppressed a ")
				.append(entity.getType().getDescription());

		for (ServerPlayer player : level.players()) {
			if (conduit.covers(player.getBlockX(), player.getBlockY(), player.getBlockZ())) {
				// Same packet /title actionbar sends (TitleCommand.showTitle).
				player.connection.send(new ClientboundSetActionBarTextPacket(message));
			}
		}
	}
}
