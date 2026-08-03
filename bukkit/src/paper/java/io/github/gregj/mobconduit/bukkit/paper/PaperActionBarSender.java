package io.github.gregj.mobconduit.bukkit.paper;

import io.github.gregj.mobconduit.bukkit.ActionBars;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Adventure action-bar sender for Paper runtimes. Lives in the paper source set because
 * Adventure is not on the spigot-api compile classpath; loaded reflectively by
 * {@code ActionBars.detect()} only when Paper and Adventure are both present.
 */
public final class PaperActionBarSender implements ActionBars.Sender {
	@Override
	public void send(Player player, String message) {
		player.sendActionBar(Component.text(message));
	}
}
