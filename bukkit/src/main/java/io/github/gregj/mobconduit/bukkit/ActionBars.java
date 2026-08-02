package io.github.gregj.mobconduit.bukkit;

import org.bukkit.entity.Player;

/**
 * Sends action-bar text on both platforms. Paper has Adventure audiences
 * ({@code player.sendActionBar(Component)}); Spigot only has the legacy
 * {@code player.spigot().sendMessage(ACTION_BAR, ...)}.
 *
 * <p>The two implementations are separate classes loaded lazily: {@link AdventureSender} is
 * instantiated only after the Paper and Adventure checks pass, so its reference to the
 * Adventure {@code Component} API never links on a Spigot runtime, and the legacy sender
 * uses only bungeecord-chat, which both platforms ship.
 */
public final class ActionBars {
	interface Sender {
		void send(Player player, String message);
	}

	private static Sender sender = new LegacySender();

	private ActionBars() {
	}

	/** Picks the sender for the platform we are running on. Called once from onEnable. */
	public static void detect() {
		sender = new LegacySender();

		if (!PaperAccess.available()) {
			return;
		}

		try {
			Class.forName("net.kyori.adventure.audience.Audience");
			sender = new AdventureSender();
		} catch (Throwable t) {
			sender = new LegacySender();
		}
	}

	public static void send(Player player, String message) {
		sender.send(player, message);
	}

	/** Paper path: Adventure audience. Loaded only behind {@link #detect()}'s checks. */
	private static final class AdventureSender implements Sender {
		@Override
		public void send(Player player, String message) {
			player.sendActionBar(net.kyori.adventure.text.Component.text(message));
		}
	}

	/** Spigot-safe path: bungeecord-chat, present on both platforms. */
	private static final class LegacySender implements Sender {
		@Override
		public void send(Player player, String message) {
			player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
					new net.md_5.bungee.api.chat.TextComponent(message));
		}
	}
}
