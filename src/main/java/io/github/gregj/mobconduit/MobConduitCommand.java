package io.github.gregj.mobconduit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * {@code /mobconduit} with seven subcommands: {@code reload}, {@code status} /
 * {@code status off}, {@code sweep}, {@code set}, {@code get}, {@code build} and
 * {@code visualize}. Plain chat output only — the mod registers nothing and a vanilla client
 * sees ordinary text.
 */
public final class MobConduitCommand {
	private MobConduitCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mobconduit")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(MobConduitCommand::reload))
				.then(Commands.literal("status")
						.executes(MobConduitCommand::status)
						.then(Commands.literal("off").executes(MobConduitCommand::statusOff)))
				.then(Commands.literal("sweep").executes(MobConduitCommand::sweep))
				.then(Commands.literal("set")
						.then(Commands.argument("key", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(ModConfig.keys(), builder))
								.then(Commands.argument("value", StringArgumentType.greedyString())
										.executes(MobConduitCommand::setConfig))))
				.then(Commands.literal("get")
						.then(Commands.argument("key", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(ModConfig.keys(), builder))
								.executes(MobConduitCommand::getConfig)))
				.then(Commands.literal("build")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.executes(context -> build(context, BlockPosArgument.getBlockPos(context, "pos")))))
			.then(Commands.literal("visualize").executes(MobConduitCommand::visualize));

		dispatcher.register(root);
	}

	/**
	 * {@code /mobconduit set <key> <value>} — sets any config entry by its JSON key, applies it
	 * live and writes the file. Runs the same conduit revalidation as {@code reload}: changing
	 * {@code frame_block} or the radius keys re-derives or drops existing conduits, and a stale
	 * entry would suppress spawning with no visible structure causing it.
	 *
	 * <p>The reply echoes the value that actually took hold, so a clamped number or an unknown
	 * block id falling back to the default is visible immediately rather than sitting silently
	 * in the log.
	 */
	private static int setConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		String key = StringArgumentType.getString(context, "key");
		String value = StringArgumentType.getString(context, "value");
		String effective;

		try {
			effective = ModConfig.set(key, value);
		} catch (IllegalArgumentException e) {
			context.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}

		int dropped = 0;

		for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
			dropped += ConduitStore.get(level).revalidate(level);
		}

		int finalDropped = dropped;
		context.getSource().sendSuccess(() -> Component.literal(key + " = " + effective + " (saved)"
				+ (finalDropped > 0 ? " — " + finalDropped + " conduit(s) no longer valid" : "")), true);
		return 1;
	}

	/**
	 * Draws every active conduit's coverage sphere in particles for a few seconds, so the edge
	 * is visible before more frame blocks go down.
	 */
	private static int visualize(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		int armed = RadiusVisualizer.arm(level, ConduitStore.get(level).conduits());

		source.sendSuccess(() -> Component.literal(armed == 0
				? "No active Mob Conduits in this dimension to visualize."
				: "Showing the coverage of " + armed + " conduit(s) for 10 seconds."), false);
		return armed;
	}

	private static int getConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		String key = StringArgumentType.getString(context, "key");

		try {
			String value = ModConfig.describe(key);
			context.getSource().sendSuccess(() -> Component.literal(key + " = " + value), false);
			return 1;
		} catch (IllegalArgumentException e) {
			context.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	/**
	 * Testing aid: erects a full frame plus obsidian and crystal, so a conduit can be stood up
	 * without hand-placing 42 blocks in ring formation.
	 *
	 * <p>{@code pos} is the obsidian. The crystal lands one block above it, which is what
	 * {@code EndCrystalItem} does, and the frame centres on the crystal.
	 */
	private static int build(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, BlockPos obsidian) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos centre = obsidian.above();
		Block frameBlock = ModConfig.get().frameBlock();
		int[] offsets = FrameShape.offsets();

		for (int i = 0; i < offsets.length; i += 3) {
			level.setBlockAndUpdate(centre.offset(offsets[i], offsets[i + 1], offsets[i + 2]), frameBlock.defaultBlockState());
		}

		level.setBlockAndUpdate(obsidian, Blocks.OBSIDIAN.defaultBlockState());
		level.setBlockAndUpdate(centre, Blocks.AIR.defaultBlockState());

		EndCrystal crystal = new EndCrystal(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
		crystal.setShowBottom(false);
		level.addFreshEntity(crystal);

		source.sendSuccess(() -> Component.literal("Built a full " + FrameShape.MAX_FRAME_BLOCKS
				+ "-block frame at " + centre.toShortString() + ". It activates within 2 seconds."), true);
		return 1;
	}

	/**
	 * Reloads the config and re-validates every known conduit. Changing {@code frame_block} can
	 * invalidate existing structures, and a stale active entry would suppress spawning with no
	 * visible structure causing it.
	 */
	private static int reload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		ModConfig config = ModConfig.load();
		int dropped = 0;

		for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
			dropped += ConduitStore.get(level).revalidate(level);
		}

		int finalDropped = dropped;
		context.getSource().sendSuccess(() -> Component.literal(
				"Mob Conduit config reloaded: frame block " + config.frameBlockName()
						+ ", radius " + config.radiusMin() + "-" + config.radiusMax()
						+ ", thresholds " + config.frameThresholdMin() + "-" + config.frameThresholdMax()
						+ (finalDropped > 0 ? " (" + finalDropped + " conduit(s) no longer valid)" : "")), true);
		return 1;
	}

	/**
	 * Re-runs the one-time erasure for every conduit in this dimension. The sweep normally only
	 * fires when a conduit arms, so this is the way to clear hostiles that were already there.
	 */
	private static int sweep(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		ConduitStore store = ConduitStore.get(level);
		int conduits = store.forceSweep(level);
		int queued = store.pendingRemovalCount();

		source.sendSuccess(() -> Component.literal(conduits == 0
				? "No active Mob Conduits in this dimension to sweep."
				: "Sweeping " + conduits + " conduit(s): " + queued + " mob(s) queued for removal."), true);
		return queued;
	}

	private static int statusOff(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		StatusBoard.disable(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.literal("Mob Conduit sidebar hidden."), false);
		return 1;
	}

	private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		ConduitStore store = ConduitStore.get(level);
		List<Conduit> conduits = store.conduits();

		if (conduits.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No active Mob Conduits in this dimension."), false);
			source.sendSuccess(() -> Component.literal("Since server start - " + SpawnStats.summary()), false);
			StatusBoard.enable(source.getServer());
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Active Mob Conduits in this dimension: " + conduits.size()), false);

		for (Conduit conduit : conduits) {
			source.sendSuccess(() -> Component.literal("  " + conduit.pos().toShortString()
					+ " - " + conduit.frameCount() + " frame blocks, radius " + conduit.radius()), false);
		}

		int pending = store.pendingRemovalCount();

		if (pending > 0) {
			source.sendSuccess(() -> Component.literal("  " + pending + " mob(s) still queued for removal"), false);
		}

		source.sendSuccess(() -> Component.literal("Since server start - " + SpawnStats.summary()), false);
		StatusBoard.enable(source.getServer());
		source.sendSuccess(() -> Component.literal("Live sidebar on. /mobconduit status off to hide it."), false);
		return conduits.size();
	}
}
