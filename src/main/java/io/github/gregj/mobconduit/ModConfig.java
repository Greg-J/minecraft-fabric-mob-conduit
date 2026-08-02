package io.github.gregj.mobconduit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
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
	private List<String> removalExemptTypes = List.of(
			"minecraft:wither", "minecraft:ender_dragon", "minecraft:warden", "minecraft:elder_guardian");
	private int removalBudgetPerTick = 32;

	/** Entity ids the conduit leaves entirely alone — neither suppressed nor swept. */
	private List<String> suppressExemptTypes = List.of();

	/** How a vetoed spawn announces itself to players in range: off, actionbar or particle. */
	private String suppressionFeedback = "actionbar";

	/**
	 * Coverage volume: {@code sphere} (3D radius, vanilla-conduit-like) or {@code cylinder}
	 * (same horizontal radius, full height — what base spawn-proofing usually wants).
	 */
	private String radiusShape = "sphere";

	/** Dimension ids where conduits do nothing, e.g. ["minecraft:the_end"]. */
	private List<String> disabledDimensions = List.of();

	private boolean activationSounds = true;
	private boolean ambientSounds = true;
	private int removalParticleCount = 40;

	/** Directional soul flames that climb out of the mob. One packet each. */
	private int removalRiserCount = 20;

	// Particle types. Any vanilla particle id that needs no extra data — see resolveParticle.
	private String crystalAuraParticle = "minecraft:trial_spawner_detection_ominous";
	private String killPlumeParticle = "minecraft:sculk_soul";
	private String frameDripParticle = "minecraft:dripping_obsidian_tear";
	private String removalParticle = "minecraft:soul_fire_flame";
	private String removalSecondaryParticle = "minecraft:soul";
	private String removalRiserParticle = "minecraft:soul_fire_flame";

	/** Continuous `trial_spawner_detection_ominous` shimmer in and around the crystal. */
	private boolean crystalAuraEnabled = true;

	private int crystalAuraCount = 6;

	private int crystalAuraIntervalTicks = 4;

	/** Soul flames off the top of the conduit per forcefield kill, across the centre 3x3. */
	private int killPlumeCount = 0;

	/**
	 * Column fired up out of the top per forcefield kill, one particle per block. Off by
	 * default — set a length to enable it. {@code sonic_boom} is the intended particle, and it
	 * only reads correctly emitted this way rather than as a scattered burst.
	 */
	private String killBeamParticle = "minecraft:sonic_boom";

	private int killBeamLength = 0;

	/** `dripping_obsidian_tear` weeping off the frame while active. */
	private boolean frameDripsEnabled = true;

	/** Frame blocks that drip per pass. Kept low so it reads as weeping, not a particle wall. */
	private int frameDripCount = 3;

	private int frameDripIntervalTicks = 8;

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

	/** Floating status text above the crystal while active — a vanilla text_display entity. */
	private boolean hologram = true;

	private boolean removalLightEnabled = true;
	private int removalLightDelayTicks = 10;
	private int removalLightFadeTicks = 60;

	/** Ceiling on lights in flight at once. 0 means unlimited. */
	private int maxConcurrentLights = 0;

	// --- resolved at validate(), not serialized -----------------------------------------

	private transient Block resolvedFrameBlock = Blocks.NETHERITE_BLOCK;
	private transient Set<EntityType<?>> resolvedExemptTypes = Set.of();
	private transient Set<EntityType<?>> resolvedSuppressExemptTypes = Set.of();
	private transient FeedbackMode resolvedSuppressionFeedback = FeedbackMode.OFF;
	private transient RadiusShape resolvedRadiusShape = RadiusShape.SPHERE;
	private transient Set<Identifier> resolvedDisabledDimensions = Set.of();

	private transient SimpleParticleType resolvedCrystalAuraParticle = ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS;
	private transient SimpleParticleType resolvedKillPlumeParticle = ParticleTypes.SCULK_SOUL;
	private transient SimpleParticleType resolvedKillBeamParticle = ParticleTypes.SONIC_BOOM;
	private transient SimpleParticleType resolvedFrameDripParticle = ParticleTypes.DRIPPING_OBSIDIAN_TEAR;
	private transient SimpleParticleType resolvedRemovalParticle = ParticleTypes.SOUL_FIRE_FLAME;
	private transient SimpleParticleType resolvedRemovalSecondaryParticle = ParticleTypes.SOUL;
	private transient SimpleParticleType resolvedRemovalRiserParticle = ParticleTypes.SOUL_FIRE_FLAME;

	public static ModConfig get() {
		return active;
	}

	/** How a vetoed spawn announces itself; see {@code suppression_feedback}. */
	public enum FeedbackMode {
		OFF,
		ACTIONBAR,
		PARTICLE
	}

	/** Coverage volume; see {@code radius_shape}. */
	public enum RadiusShape {
		SPHERE,
		CYLINDER
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
	 * Sets one entry by its JSON key, revalidates the whole config, installs it and writes the
	 * file. Driven through the Gson tree rather than a hand-maintained switch, so every
	 * serialized field — current and future — is settable by exactly the name that appears in
	 * the file, and transient resolved state can never be named.
	 *
	 * <p>Returns the effective value after validation, which is what actually took hold:
	 * numbers come back clamped, and an unknown block or particle id comes back as the default
	 * it fell back to.
	 *
	 * @throws IllegalArgumentException for an unknown key or an unparseable value.
	 */
	public static String set(String key, String rawValue) {
		JsonObject tree = GSON.toJsonTree(active).getAsJsonObject();
		JsonElement existing = tree.get(key);

		if (existing == null) {
			throw new IllegalArgumentException("Unknown setting '" + key + "'");
		}

		tree.add(key, parseLike(existing, rawValue));

		ModConfig config = GSON.fromJson(tree, ModConfig.class);
		config.validate();
		active = config;
		config.save();

		return GSON.toJsonTree(config).getAsJsonObject().get(key).toString();
	}

	/** Current value of one entry, as it appears in the file. */
	public static String describe(String key) {
		JsonElement value = GSON.toJsonTree(active).getAsJsonObject().get(key);

		if (value == null) {
			throw new IllegalArgumentException("Unknown setting '" + key + "'");
		}

		return value.toString();
	}

	/** The settable keys, i.e. exactly the keys the config file holds. */
	public static Set<String> keys() {
		return GSON.toJsonTree(active).getAsJsonObject().keySet();
	}

	/**
	 * Parses a raw command value against the type of the entry it replaces. Lists are
	 * comma-separated ({@code none} or {@code []} to clear); this config only has lists of
	 * strings.
	 */
	private static JsonElement parseLike(JsonElement existing, String raw) {
		if (existing.isJsonArray()) {
			JsonArray array = new JsonArray();

			if (!raw.equals("none") && !raw.equals("[]")) {
				for (String part : raw.split(",")) {
					array.add(part.trim());
				}
			}

			return array;
		}

		JsonPrimitive primitive = existing.getAsJsonPrimitive();

		if (primitive.isBoolean()) {
			if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
				throw new IllegalArgumentException("'" + raw + "' is not true or false");
			}

			return new JsonPrimitive(Boolean.parseBoolean(raw));
		}

		if (primitive.isNumber()) {
			try {
				return new JsonPrimitive(new BigDecimal(raw));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("'" + raw + "' is not a number");
			}
		}

		return new JsonPrimitive(raw);
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
		this.resolvedCrystalAuraParticle = resolveParticle(this.crystalAuraParticle, "crystal_aura_particle", ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS);
		this.resolvedKillPlumeParticle = resolveParticle(this.killPlumeParticle, "kill_plume_particle", ParticleTypes.SCULK_SOUL);
		this.resolvedKillBeamParticle = resolveParticle(this.killBeamParticle, "kill_beam_particle", ParticleTypes.SONIC_BOOM);
		this.resolvedFrameDripParticle = resolveParticle(this.frameDripParticle, "frame_drip_particle", ParticleTypes.DRIPPING_OBSIDIAN_TEAR);
		this.resolvedRemovalParticle = resolveParticle(this.removalParticle, "removal_particle", ParticleTypes.SOUL_FIRE_FLAME);
		this.resolvedRemovalSecondaryParticle = resolveParticle(this.removalSecondaryParticle, "removal_secondary_particle", ParticleTypes.SOUL);
		this.resolvedRemovalRiserParticle = resolveParticle(this.removalRiserParticle, "removal_riser_particle", ParticleTypes.SOUL_FIRE_FLAME);

		this.crystalAuraCount = clamp(this.crystalAuraCount, 0, 128);
		this.crystalAuraIntervalTicks = clamp(this.crystalAuraIntervalTicks, 1, 200);
		this.killPlumeCount = clamp(this.killPlumeCount, 0, 512);
		this.killBeamLength = clamp(this.killBeamLength, 0, 64);
		this.frameDripCount = clamp(this.frameDripCount, 0, 42);
		this.frameDripIntervalTicks = clamp(this.frameDripIntervalTicks, 1, 200);
		this.removalRiserSpeed = Math.max(0.0, Math.min(4.0, this.removalRiserSpeed));
		this.forcefieldIntervalTicks = clamp(this.forcefieldIntervalTicks, 5, 1200);
		this.removalLightDelayTicks = clamp(this.removalLightDelayTicks, 0, 200);
		// The fade walks one light level per step, so anything under 15 ticks collapses to 15
		// anyway; say so in the clamp rather than silently misbehaving.
		this.removalLightFadeTicks = clamp(this.removalLightFadeTicks, 15, 600);
		this.maxConcurrentLights = clamp(this.maxConcurrentLights, 0, 8192);

		this.resolvedExemptTypes = resolveTypeSet(this.removalExemptTypes, "removal_exempt_types");
		this.resolvedSuppressExemptTypes = resolveTypeSet(this.suppressExemptTypes, "suppress_exempt_types");
		this.resolvedSuppressionFeedback = resolveEnum(this.suppressionFeedback, FeedbackMode.class, "suppression_feedback", FeedbackMode.OFF);
		this.resolvedRadiusShape = resolveEnum(this.radiusShape, RadiusShape.class, "radius_shape", RadiusShape.SPHERE);
		this.resolvedDisabledDimensions = resolveDimensions(this.disabledDimensions);
	}

	private static Set<EntityType<?>> resolveTypeSet(List<String> names, String key) {
		Set<EntityType<?>> resolved = new HashSet<>();

		if (names != null) {
			for (String name : names) {
				Identifier id = tryParse(name);

				if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					MobConduit.LOGGER.error("{}: '{}' is not a known entity type; ignoring", key, name);
					continue;
				}

				resolved.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
			}
		}

		return Set.copyOf(resolved);
	}

	private static <E extends Enum<E>> E resolveEnum(String name, Class<E> type, String key, E fallback) {
		try {
			return Enum.valueOf(type, name.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			MobConduit.LOGGER.error("{}: '{}' is not one of {}; falling back to {}",
					key, name, java.util.Arrays.toString(type.getEnumConstants()).toLowerCase(java.util.Locale.ROOT), fallback.name().toLowerCase(java.util.Locale.ROOT));
			return fallback;
		}
	}

	private static Set<Identifier> resolveDimensions(List<String> names) {
		Set<Identifier> resolved = new HashSet<>();

		if (names != null) {
			for (String name : names) {
				Identifier id = tryParse(name);

				if (id == null) {
					MobConduit.LOGGER.error("disabled_dimensions: '{}' is not a valid identifier; ignoring", name);
					continue;
				}

				resolved.add(id);
			}
		}

		return Set.copyOf(resolved);
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

		Block block = BuiltInRegistries.BLOCK.getValue(id);

		if (block.defaultBlockState().isAir()) {
			// The 42 frame positions are air by default, so an air frame block would activate
			// any crystal anywhere at full radius for free.
			MobConduit.LOGGER.error("frame_block: '{}' is an air block; falling back to minecraft:netherite_block", name);
			return Blocks.NETHERITE_BLOCK;
		}

		return block;
	}

	/**
	 * Resolves a particle id, falling back on anything unusable.
	 *
	 * <p>Only {@link SimpleParticleType} works here. It is the one particle class that is both a
	 * registry entry and a {@code ParticleOptions} ({@code SimpleParticleType.java:7}); types
	 * like {@code dust}, {@code block} and {@code item} carry extra data that an id alone cannot
	 * supply, so naming one is a config error rather than something to guess at.
	 */
	private static SimpleParticleType resolveParticle(String name, String key, SimpleParticleType fallback) {
		Identifier id = tryParse(name);

		if (id == null) {
			MobConduit.LOGGER.error("{}: '{}' is not a valid identifier; falling back to the default", key, name);
			return fallback;
		}

		if (!BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) {
			MobConduit.LOGGER.error("{}: '{}' is not a known particle; falling back to the default", key, name);
			return fallback;
		}

		if (BuiltInRegistries.PARTICLE_TYPE.getValue(id) instanceof SimpleParticleType simple) {
			return simple;
		}

		MobConduit.LOGGER.error("{}: '{}' needs extra data and cannot be set by id alone; falling back to the default", key, name);
		return fallback;
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

	public boolean isExemptFromSuppression(EntityType<?> type) {
		return this.resolvedSuppressExemptTypes.contains(type);
	}

	public FeedbackMode suppressionFeedback() {
		return this.resolvedSuppressionFeedback;
	}

	public RadiusShape radiusShape() {
		return this.resolvedRadiusShape;
	}

	public boolean isDimensionDisabled(Identifier dimension) {
		return this.resolvedDisabledDimensions.contains(dimension);
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

	public boolean hologram() {
		return this.hologram;
	}

	public int removalRiserCount() {
		return this.removalRiserCount;
	}

	public SimpleParticleType crystalAuraParticle() {
		return this.resolvedCrystalAuraParticle;
	}

	public SimpleParticleType killPlumeParticle() {
		return this.resolvedKillPlumeParticle;
	}

	public SimpleParticleType killBeamParticle() {
		return this.resolvedKillBeamParticle;
	}

	public int killBeamLength() {
		return this.killBeamLength;
	}

	public SimpleParticleType frameDripParticle() {
		return this.resolvedFrameDripParticle;
	}

	public SimpleParticleType removalParticle() {
		return this.resolvedRemovalParticle;
	}

	public SimpleParticleType removalSecondaryParticle() {
		return this.resolvedRemovalSecondaryParticle;
	}

	public SimpleParticleType removalRiserParticle() {
		return this.resolvedRemovalRiserParticle;
	}

	public boolean crystalAuraEnabled() {
		return this.crystalAuraEnabled;
	}

	public int crystalAuraCount() {
		return this.crystalAuraCount;
	}

	public int crystalAuraIntervalTicks() {
		return this.crystalAuraIntervalTicks;
	}

	public int killPlumeCount() {
		return this.killPlumeCount;
	}

	public boolean frameDripsEnabled() {
		return this.frameDripsEnabled;
	}

	public int frameDripCount() {
		return this.frameDripCount;
	}

	public int frameDripIntervalTicks() {
		return this.frameDripIntervalTicks;
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
