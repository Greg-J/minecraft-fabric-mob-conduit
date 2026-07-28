package io.github.gregj.mobconduit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side JSON config. Read at startup and on {@code /mobconduit reload}; never synced,
 * because the mod is server-only.
 *
 * <p>Fields are deserialized by Gson under
 * {@link FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES}, so {@code frameBlock} in Java is
 * {@code frame_block} in JSON.
 */
public final class ModConfig {
	/**
	 * Vanilla's conduit activation threshold, {@code MIN_ACTIVE_SIZE} in
	 * {@code ConduitBlockEntity} (26.2).
	 */
	public static final int VANILLA_ACTIVATION_FRAME_COUNT = 16;

	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private static volatile ModConfig active = new ModConfig();

	// --- serialized ---------------------------------------------------------------------

	private String frameBlock = "minecraft:netherite_block";
	private int radiusMin = 64;
	private int radiusMax = 128;
	private int frameThresholdMin = VANILLA_ACTIVATION_FRAME_COUNT;
	private int frameThresholdMax = FrameShape.MAX_FRAME_BLOCKS;
	private List<String> removalExemptTypes = List.of("minecraft:wither", "minecraft:ender_dragon");
	private int removalBudgetPerTick = 32;

	private boolean activationSounds = true;
	private boolean ambientSounds = true;
	private int removalParticleCount = 40;

	/** Directional soul flames that climb out of the mob. One packet each. */
	private int removalRiserCount = 20;

	/** Upward velocity per riser. ~1.0 climbs roughly 10-21 blocks before the particle expires. */
	private double removalRiserSpeed = 1.0;

	/**
	 * When true the conduit also erases hostiles that wander in, not only those present at
	 * activation.
	 *
	 * <p>On by default. Measured on a live server, spawn suppression alone cancelled 5142 of
	 * 5310 natural hostile spawn attempts; the remaining 168 spawned outside the sphere and
	 * walked in, which is exactly what a player reads as "it isn't working". Closing that gap
	 * costs a radius scan per conduit per {@link #forcefieldIntervalTicks}, against plain
	 * suppression costing nothing per tick.
	 */
	private boolean forcefield = true;

	private int forcefieldIntervalTicks = 40;

	/**
	 * Kill erased mobs instead of discarding them, so they drop loot and XP.
	 *
	 * <p>Off by default, and deliberately so: a full-radius activation can erase hundreds of
	 * hostiles, and killing them drops hundreds of item stacks and XP orbs that then sit in the
	 * world for five minutes. Turning this on converts a one-off tick cost into a sustained one.
	 */
	private boolean removalDrops = false;

	/**
	 * Swap the obsidian under the crystal for a light block while active. Reverted on
	 * deactivation, because {@code EndCrystalItem} will only place a crystal on obsidian or
	 * bedrock and the player needs that block back to rebuild.
	 */
	private boolean lightBaseOnActivate = true;

	private boolean removalLightEnabled = true;
	private int removalLightDelayTicks = 10;
	private int removalLightFadeTicks = 60;

	/** Ceiling on lights in flight at once. 0 means unlimited. */
	private int maxConcurrentLights = 0;

	// --- resolved at validate(), not serialized -----------------------------------------

	private transient Block resolvedFrameBlock = Blocks.NETHERITE_BLOCK;
	private transient Set<EntityType<?>> resolvedExemptTypes = Set.of();

	public static ModConfig get() {
		return active;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(MobConduit.MOD_ID + ".json");
	}

	/**
	 * Reads the config from disk, validates it, and installs it as the active config. Writes a
	 * default file if none exists. Never throws: a broken file falls back to defaults so the
	 * server still boots.
	 */
	public static ModConfig load() {
		Path path = configPath();
		ModConfig config = new ModConfig();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				ModConfig parsed = GSON.fromJson(reader, ModConfig.class);

				if (parsed != null) {
					config = parsed;
				} else {
					MobConduit.LOGGER.error("{} is empty; using defaults", path);
				}
			} catch (IOException | RuntimeException e) {
				MobConduit.LOGGER.error("Failed to read {}; using defaults", path, e);
			}
		}

		config.validate();
		active = config;

		if (!Files.exists(path)) {
			config.save();
		}

		return config;
	}

	private void save() {
		Path path = configPath();

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			MobConduit.LOGGER.error("Failed to write default config to {}", path, e);
		}
	}

	/**
	 * Clamps numeric fields into sane ranges and resolves registry names. Registry lookups use
	 * {@code containsKey} rather than {@code getValue}, because {@code BuiltInRegistries.BLOCK}
	 * is a {@code DefaultedRegistry} that silently answers AIR for an unknown id.
	 */
	private void validate() {
		this.resolvedFrameBlock = resolveBlock(this.frameBlock);

		if (this.resolvedFrameBlock == Blocks.NETHERITE_BLOCK) {
			this.frameBlock = "minecraft:netherite_block";
		}

		this.frameThresholdMin = clamp(this.frameThresholdMin, 1, FrameShape.MAX_FRAME_BLOCKS);
		this.frameThresholdMax = clamp(this.frameThresholdMax, this.frameThresholdMin, FrameShape.MAX_FRAME_BLOCKS);
		this.radiusMin = clamp(this.radiusMin, 1, 512);
		this.radiusMax = clamp(this.radiusMax, this.radiusMin, 512);
		this.removalBudgetPerTick = clamp(this.removalBudgetPerTick, 1, 4096);
		this.removalParticleCount = clamp(this.removalParticleCount, 0, 256);
		this.removalRiserCount = clamp(this.removalRiserCount, 0, 128);
		this.removalRiserSpeed = Math.max(0.0, Math.min(4.0, this.removalRiserSpeed));
		this.forcefieldIntervalTicks = clamp(this.forcefieldIntervalTicks, 5, 1200);
		this.removalLightDelayTicks = clamp(this.removalLightDelayTicks, 0, 200);
		this.removalLightFadeTicks = clamp(this.removalLightFadeTicks, 1, 600);
		this.maxConcurrentLights = clamp(this.maxConcurrentLights, 0, 8192);

		Set<EntityType<?>> exempt = new HashSet<>();

		if (this.removalExemptTypes != null) {
			for (String name : this.removalExemptTypes) {
				Identifier id = tryParse(name);

				if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					MobConduit.LOGGER.error("removal_exempt_types: '{}' is not a known entity type; ignoring", name);
					continue;
				}

				exempt.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
			}
		}

		this.resolvedExemptTypes = Set.copyOf(exempt);
	}

	private static Block resolveBlock(String name) {
		Identifier id = tryParse(name);

		if (id == null) {
			MobConduit.LOGGER.error("frame_block: '{}' is not a valid identifier; falling back to minecraft:netherite_block", name);
			return Blocks.NETHERITE_BLOCK;
		}

		if (!BuiltInRegistries.BLOCK.containsKey(id)) {
			MobConduit.LOGGER.error("frame_block: '{}' is not a known block; falling back to minecraft:netherite_block", name);
			return Blocks.NETHERITE_BLOCK;
		}

		return BuiltInRegistries.BLOCK.getValue(id);
	}

	private static Identifier tryParse(String name) {
		if (name == null) {
			return null;
		}

		try {
			return Identifier.parse(name);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * Radius for a given frame block count, interpolated linearly between the two thresholds.
	 * Returns 0 when the frame is too small to activate.
	 */
	public int radiusFor(int frameCount) {
		if (frameCount < this.frameThresholdMin) {
			return 0;
		}

		if (frameCount >= this.frameThresholdMax || this.frameThresholdMax == this.frameThresholdMin) {
			return this.radiusMax;
		}

		int span = this.frameThresholdMax - this.frameThresholdMin;
		return this.radiusMin + (this.radiusMax - this.radiusMin) * (frameCount - this.frameThresholdMin) / span;
	}

	public Block frameBlock() {
		return this.resolvedFrameBlock;
	}

	public String frameBlockName() {
		return this.frameBlock;
	}

	public int frameThresholdMin() {
		return this.frameThresholdMin;
	}

	public int frameThresholdMax() {
		return this.frameThresholdMax;
	}

	public int radiusMin() {
		return this.radiusMin;
	}

	public int radiusMax() {
		return this.radiusMax;
	}

	public int removalBudgetPerTick() {
		return this.removalBudgetPerTick;
	}

	public boolean isExemptFromRemoval(EntityType<?> type) {
		return this.resolvedExemptTypes.contains(type);
	}

	public boolean activationSounds() {
		return this.activationSounds;
	}

	public boolean ambientSounds() {
		return this.ambientSounds;
	}

	public int removalParticleCount() {
		return this.removalParticleCount;
	}

	public boolean lightBaseOnActivate() {
		return this.lightBaseOnActivate;
	}

	public int removalRiserCount() {
		return this.removalRiserCount;
	}

	public double removalRiserSpeed() {
		return this.removalRiserSpeed;
	}

	public boolean forcefield() {
		return this.forcefield;
	}

	public boolean removalDrops() {
		return this.removalDrops;
	}

	public int forcefieldIntervalTicks() {
		return this.forcefieldIntervalTicks;
	}

	public boolean removalLightEnabled() {
		return this.removalLightEnabled;
	}

	public int removalLightDelayTicks() {
		return this.removalLightDelayTicks;
	}

	public int removalLightFadeTicks() {
		return this.removalLightFadeTicks;
	}

	public int maxConcurrentLights() {
		return this.maxConcurrentLights;
	}
}
