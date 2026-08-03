package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /mobconduit} with seven subcommands: {@code reload}, {@code status} /
 * {@code status off}, {@code sweep}, {@code set}, {@code get}, {@code build} and
 * {@code visualize}. Plain chat output only — the plugin registers nothing and a vanilla
 * client sees ordinary text.
 *
 * <p>Two registration paths, one handler: on Spigot the command comes from plugin.yml; on
 * Paper the jar loads as a paper-plugin (paper-plugin.yml wins over plugin.yml), and paper
 * plugins never read the plugin.yml commands section, so the command is registered with the
 * command map directly instead.
 */
public final class MobConduitCommand implements CommandExecutor, TabCompleter {
	private static final String PERMISSION = "mobconduit.admin";
	private static final List<String> SUBCOMMANDS = List.of(
			"reload", "status", "sweep", "set", "get", "build", "visualize");

	private final MobConduitPlugin plugin;

	public MobConduitCommand(MobConduitPlugin plugin) {
		this.plugin = plugin;
	}

	/** Errors render red, matching what vanilla's sendFailure does on the loaders. */
	private static void fail(CommandSender sender, String message) {
		sender.sendMessage(ChatColor.RED + message);
	}

	void register() {
		// plugin.yml declares this node for Spigot; the paper-plugin path has no descriptor
		// permissions section, so define it programmatically there.
		if (Bukkit.getPluginManager().getPermission(PERMISSION) == null) {
			Bukkit.getPluginManager().addPermission(
					new Permission(PERMISSION, "Mob Conduit admin commands.", PermissionDefault.OP));
		}

		// Paper loads the jar as a paper-plugin (paper-plugin.yml wins over plugin.yml), and
		// JavaPlugin#getCommand throws on a paper-plugin — paper plugins never read the
		// plugin.yml commands section — so the command goes into the command map directly.
		// Server#getCommandMap is Paper-only API, reached through the paper source set.
		if (PaperAccess.available()) {
			PaperAccess.registerCommand("mobconduit", new Dispatch("mobconduit"));
			return;
		}

		// Spigot: the plugin.yml command. setExecutor/setTabCompleter is all it needs.
		PluginCommand command = this.plugin.getCommand("mobconduit");

		if (command != null) {
			command.setExecutor(this);
			command.setTabCompleter(this);
		}
	}

	/** Command-map dispatch used when plugin.yml commands are not in play (Paper). */
	private final class Dispatch extends Command {
		private Dispatch(String name) {
			super(name);
			setDescription("Mob Conduit administration.");
			setUsage("/mobconduit <reload|status|sweep|set|get|build|visualize>");
			setPermission(PERMISSION);
		}

		@Override
		public boolean execute(CommandSender sender, String label, String[] args) {
			return MobConduitCommand.this.onCommand(sender, this, label, args);
		}

		@Override
		public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
			return MobConduitCommand.this.onTabComplete(sender, this, alias, args);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage("Usage: /mobconduit <reload|status|sweep|set|get|build|visualize>");
			return true;
		}

		switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
			case "reload" -> reload(sender);
			case "status" -> status(sender, args);
			case "sweep" -> sweep(sender);
			case "set" -> setConfig(sender, args);
			case "get" -> getConfig(sender, args);
			case "build" -> build(sender, args);
			case "visualize" -> visualize(sender);
			default -> sender.sendMessage("Unknown subcommand '" + args[0]
					+ "'. Usage: /mobconduit <reload|status|sweep|set|get|build|visualize>");
		}

		return true;
	}

	/**
	 * Reloads the config and re-validates every known conduit. Changing {@code frame_block}
	 * can invalidate existing structures, and a stale active entry would suppress spawning
	 * with no visible structure causing it.
	 */
	private void reload(CommandSender sender) {
		ModConfig config = ModConfig.load();
		int dropped = revalidateAll();

		sender.sendMessage("Mob Conduit config reloaded: frame block " + config.frameBlockName()
				+ ", radius " + config.radiusMin() + "-" + config.radiusMax()
				+ ", thresholds " + config.frameThresholdMin() + "-" + config.frameThresholdMax()
				+ (dropped > 0 ? " (" + dropped + " conduit(s) no longer valid)" : ""));
	}

	private static int revalidateAll() {
		int dropped = 0;

		for (World world : Bukkit.getWorlds()) {
			dropped += ConduitStore.get(world).revalidate(world);
		}

		return dropped;
	}

	private void status(CommandSender sender, String[] args) {
		if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
			StatusBoard.disable();
			sender.sendMessage("Mob Conduit sidebar hidden.");
			return;
		}

		World world = senderWorld(sender);
		ConduitStore store = ConduitStore.get(world);
		List<Conduit> conduits = store.conduits();

		if (conduits.isEmpty()) {
			sender.sendMessage("No active Mob Conduits in this dimension.");
			sender.sendMessage("Since server start - " + SpawnStats.summary());
			StatusBoard.enable();
			return;
		}

		sender.sendMessage("Active Mob Conduits in this dimension: " + conduits.size());

		for (Conduit conduit : conduits) {
			sender.sendMessage("  " + conduit.pos()
					+ " - " + conduit.frameCount() + " frame blocks, radius " + conduit.radius()
					+ (conduit.cylindrical() ? " (cylinder)" : ""));
		}

		int pending = store.pendingRemovalCount();

		if (pending > 0) {
			sender.sendMessage("  " + pending + " mob(s) still queued for removal");
		}

		sender.sendMessage("Since server start - " + SpawnStats.summary());
		StatusBoard.enable();
		sender.sendMessage("Live sidebar on. /mobconduit status off to hide it.");
	}

	/**
	 * Re-runs the one-time erasure for every conduit in this dimension. The sweep normally
	 * only fires when a conduit arms, so this is the way to clear hostiles that were already
	 * there.
	 */
	private void sweep(CommandSender sender) {
		World world = senderWorld(sender);
		ConduitStore store = ConduitStore.get(world);
		int conduits = store.forceSweep(world);
		int queued = store.pendingRemovalCount();

		sender.sendMessage(conduits == 0
				? "No active Mob Conduits in this dimension to sweep."
				: "Sweeping " + conduits + " conduit(s): " + queued + " mob(s) queued for removal.");
	}

	/**
	 * {@code set <key> <value>} — sets any config entry by its JSON key, applies it live and
	 * writes the file. Runs the same conduit revalidation as {@code reload}: changing
	 * {@code frame_block} or the radius keys re-derives or drops existing conduits, and a
	 * stale entry would suppress spawning with no visible structure causing it.
	 *
	 * <p>The reply echoes the value that actually took hold, so a clamped number or an
	 * unknown block id falling back to the default is visible immediately rather than sitting
	 * silently in the log.
	 */
	private void setConfig(CommandSender sender, String[] args) {
		if (args.length < 3) {
			fail(sender, "Usage: /mobconduit set <key> <value>");
			return;
		}

		String key = args[1];
		String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
		String effective;

		try {
			effective = ModConfig.set(key, value);
		} catch (IllegalArgumentException e) {
			fail(sender, e.getMessage());
			return;
		}

		int dropped = revalidateAll();
		sender.sendMessage(key + " = " + effective + " (saved)"
				+ (dropped > 0 ? " — " + dropped + " conduit(s) no longer valid" : ""));
	}

	private void getConfig(CommandSender sender, String[] args) {
		if (args.length < 2) {
			fail(sender, "Usage: /mobconduit get <key>");
			return;
		}

		try {
			sender.sendMessage(args[1] + " = " + ModConfig.describe(args[1]));
		} catch (IllegalArgumentException e) {
			fail(sender, e.getMessage());
		}
	}

	/**
	 * Testing aid: erects a full frame plus obsidian and crystal, so a conduit can be stood
	 * up without hand-placing 42 blocks in ring formation.
	 *
	 * <p>{@code x y z} is the obsidian. The crystal lands one block above it, which is what
	 * {@code EndCrystalItem} does, and the frame centres on the crystal.
	 */
	private void build(CommandSender sender, String[] args) {
		if (args.length < 4) {
			fail(sender, "Usage: /mobconduit build <x> <y> <z>");
			return;
		}

		World world = senderWorld(sender);
		Location origin = senderLocation(sender, world);
		int x;
		int y;
		int z;

		try {
			if (args[1].startsWith("^") && args[2].startsWith("^") && args[3].startsWith("^")) {
				int[] local = parseCaretCoordinates(args, origin, sourceDirection(sender));
				x = local[0];
				y = local[1];
				z = local[2];
			} else {
				x = parseCoordinate(args[1], origin.getBlockX());
				y = parseCoordinate(args[2], origin.getBlockY());
				z = parseCoordinate(args[3], origin.getBlockZ());
			}
		} catch (NumberFormatException e) {
			fail(sender, "Coordinates must be integers, ~ relative or ^ local, got '" + e.getMessage() + "'");
			return;
		}

		ConduitPos centre = new ConduitPos(x, y + 1, z);
		Material frameBlock = ModConfig.get().frameBlock();
		int[] offsets = FrameShape.offsets();

		for (int i = 0; i < offsets.length; i += 3) {
			world.getBlockAt(centre.x() + offsets[i], centre.y() + offsets[i + 1], centre.z() + offsets[i + 2])
					.setType(frameBlock);
		}

		world.getBlockAt(x, y, z).setType(Material.OBSIDIAN);
		world.getBlockAt(centre.x(), centre.y(), centre.z()).setType(Material.AIR);

		EnderCrystal crystal = world.spawn(
				new Location(world, centre.centerX(), centre.y(), centre.centerZ()), EnderCrystal.class);
		crystal.setShowingBottom(false);
		ConduitDetector.track(crystal);

		sender.sendMessage("Built a full " + FrameShape.MAX_FRAME_BLOCKS
				+ "-block frame at " + centre + ". It activates within 2 seconds.");
	}

	private static int parseCoordinate(String raw, int origin) {
		if (raw.equals("~")) {
			return origin;
		}

		if (raw.startsWith("~")) {
			return origin + Integer.parseInt(raw.substring(1));
		}

		return Integer.parseInt(raw);
	}

	/** The sender's look direction; command blocks and console face south, vanilla's fallback. */
	private static org.bukkit.util.Vector sourceDirection(CommandSender sender) {
		if (sender instanceof Entity entity) {
			return entity.getLocation().getDirection();
		}

		return new org.bukkit.util.Vector(0, 0, 1);
	}

	/**
	 * Vanilla local coordinates: {@code ^left ^up ^forwards} relative to the look direction.
	 * Like vanilla, they are all-or-nothing — mixing {@code ^} with {@code ~} is rejected by
	 * the caller branching on every argument starting with {@code ^}.
	 */
	private static int[] parseCaretCoordinates(String[] args, Location origin, org.bukkit.util.Vector forward) {
		double left = parseCaretComponent(args[1]);
		double up = parseCaretComponent(args[2]);
		double ahead = parseCaretComponent(args[3]);

		org.bukkit.util.Vector flat = forward.clone().setY(0);

		if (flat.lengthSquared() < 1.0e-8) {
			// Looking straight up or down: vanilla still needs a horizontal axis.
			flat = new org.bukkit.util.Vector(0, 0, 1);
		}

		flat.normalize();
		// up × forward, not forward × up: facing south (+Z), local +X is east, the left hand.
		org.bukkit.util.Vector leftAxis = new org.bukkit.util.Vector(0, 1, 0).crossProduct(flat);
		org.bukkit.util.Vector result = origin.toVector()
				.add(leftAxis.multiply(left))
				.add(new org.bukkit.util.Vector(0, up, 0))
				.add(flat.multiply(ahead));

		return new int[] {result.getBlockX(), result.getBlockY(), result.getBlockZ()};
	}

	private static double parseCaretComponent(String raw) {
		return raw.equals("^") ? 0.0 : Double.parseDouble(raw.substring(1));
	}

	/**
	 * Draws every active conduit's coverage volume in particles for a few seconds, so the
	 * edge is visible before more frame blocks go down.
	 */
	private void visualize(CommandSender sender) {
		World world = senderWorld(sender);
		int armed = RadiusVisualizer.arm(world, ConduitStore.get(world).conduits());

		sender.sendMessage(armed == 0
				? "No active Mob Conduits in this dimension to visualize."
				: "Showing the coverage of " + armed + " conduit(s) for 10 seconds.");
	}

	/** The sender's world: their own for entities and command blocks, the default world for console. */
	private static World senderWorld(CommandSender sender) {
		if (sender instanceof Entity entity) {
			return entity.getWorld();
		}

		if (sender instanceof BlockCommandSender block) {
			return block.getBlock().getWorld();
		}

		return Bukkit.getWorlds().get(0);
	}

	private static Location senderLocation(CommandSender sender, World world) {
		if (sender instanceof Entity entity) {
			return entity.getLocation();
		}

		if (sender instanceof BlockCommandSender block) {
			return block.getBlock().getLocation();
		}

		return world.getSpawnLocation();
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1) {
			return filter(SUBCOMMANDS, args[0]);
		}

		if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("get"))) {
			return filter(new ArrayList<>(ModConfig.keys()), args[1]);
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
			return filter(List.of("off"), args[1]);
		}

		if (args.length >= 2 && args.length <= 4 && args[0].equalsIgnoreCase("build")) {
			return filter(List.of("~"), args[args.length - 1]);
		}

		return List.of();
	}

	private static List<String> filter(List<String> options, String prefix) {
		String lower = prefix.toLowerCase(java.util.Locale.ROOT);
		List<String> matches = new ArrayList<>();

		for (String option : options) {
			if (option.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
				matches.add(option);
			}
		}

		return matches;
	}
}
