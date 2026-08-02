package io.github.gregj.mobconduit.bukkit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side JSON config. Read at startup and on {@code /mobconduit reload}; never synced,
 * because the plugin is server-only.
 *
 * <p>Fields are deserialized by Gson under
 * {@link FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES}, so {@code frameBlock} in Java is
 * {@code frame_block} in JSON. Same schema, keys, defaults and clamps as the Fabric mod's
 * {@code ModConfig}.
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

	private transient Material resolvedFrameBlock = Material.NETHERITE_BLOCK;
	private transient Set<EntityType> resolvedExemptTypes = Set.of();
	private transient Set<EntityType> resolvedSuppressExemptTypes = Set.of();
	private transient FeedbackMode resolvedSuppressionFeedback = FeedbackMode.OFF;
	private transient RadiusShape resolvedRadiusShape = RadiusShape.SPHERE;
	private transient Set<String> resolvedDisabledDimensions = Set.of();

	private transient Particle resolvedCrystalAuraParticle = Particle.TRIAL_SPAWNER_DETECTION_OMINOUS;
	private transient Particle resolvedKillPlumeParticle = Particle.SCULK_SOUL;
	private transient Particle resolvedKillBeamParticle = Particle.SONIC_BOOM;
	private transient Particle resolvedFrameDripParticle = Particle.DRIPPING_OBSIDIAN_TEAR;
	private transient Particle resolvedRemovalParticle = Particle.SOUL_FIRE_FLAME;
	private transient Particle resolvedRemovalSecondaryParticle = Particle.SOUL;
	private transient Particle resolvedRemovalRiserParticle = Particle.SOUL_FIRE_FLAME;

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

	private static File configFile() {
		return new File(MobConduitPlugin.instance().getDataFolder(), "mob-conduit.json");
	}

	/**
	 * Reads the config from disk, validates it, and installs it as the active config. Writes a
	 * default file if none exists. Never throws: a broken file falls back to defaults so the
	 * server still boots.
	 */
	public static ModConfig load() {
		File file = configFile();
		ModConfig config = new ModConfig();

		if (file.isFile()) {
			try (Reader reader = Files.newBufferedReader(file.toPath())) {
				ModConfig parsed = GSON.fromJson(reader, ModConfig.class);

				if (parsed != null) {
					config = parsed;
				} else {
					MobConduitPlugin.logger().severe(file + " is empty; using defaults");
				}
			} catch (IOException | RuntimeException e) {
				MobConduitPlugin.logger().severe("Failed to read " + file + "; using defaults: " + e);
			}
		}

		config.validate();
		active = config;

		if (!file.isFile()) {
			config.save();
		}

		return config;
	}

	private void save() {
		File file = configFile();

		try {
			File parent = file.getParentFile();

			if (parent != null) {
				Files.createDirectories(parent.toPath());
			}

			try (Writer writer = Files.newBufferedWriter(file.toPath())) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			MobConduitPlugin.logger().severe("Failed to write default config to " + file + ": " + e);
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
	 * Clamps numeric fields into sane ranges and resolves registry names. Lookups go through
	 * {@link Registry#get(NamespacedKey)}, which returns null for an unknown id rather than a
	 * default.
	 */
	private void validate() {
		this.resolvedFrameBlock = resolveBlock(this.frameBlock);

		if (this.resolvedFrameBlock == Material.NETHERITE_BLOCK) {
			this.frameBlock = "minecraft:netherite_block";
		}

		this.frameThresholdMin = clamp(this.frameThresholdMin, 1, FrameShape.MAX_FRAME_BLOCKS);
		this.frameThresholdMax = clamp(this.frameThresholdMax, this.frameThresholdMin, FrameShape.MAX_FRAME_BLOCKS);
		this.radiusMin = clamp(this.radiusMin, 1, 512);
		this.radiusMax = clamp(this.radiusMax, this.radiusMin, 512);
		this.removalBudgetPerTick = clamp(this.removalBudgetPerTick, 1, 4096);
		this.removalParticleCount = clamp(this.removalParticleCount, 0, 256);
		this.removalRiserCount = clamp(this.removalRiserCount, 0, 128);
		this.resolvedCrystalAuraParticle = resolveParticle(this.crystalAuraParticle, "crystal_aura_particle", Particle.TRIAL_SPAWNER_DETECTION_OMINOUS);
		this.resolvedKillPlumeParticle = resolveParticle(this.killPlumeParticle, "kill_plume_particle", Particle.SCULK_SOUL);
		this.resolvedKillBeamParticle = resolveParticle(this.killBeamParticle, "kill_beam_particle", Particle.SONIC_BOOM);
		this.resolvedFrameDripParticle = resolveParticle(this.frameDripParticle, "frame_drip_particle", Particle.DRIPPING_OBSIDIAN_TEAR);
		this.resolvedRemovalParticle = resolveParticle(this.removalParticle, "removal_particle", Particle.SOUL_FIRE_FLAME);
		this.resolvedRemovalSecondaryParticle = resolveParticle(this.removalSecondaryParticle, "removal_secondary_particle", Particle.SOUL);
		this.resolvedRemovalRiserParticle = resolveParticle(this.removalRiserParticle, "removal_riser_particle", Particle.SOUL_FIRE_FLAME);

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

	private static Set<EntityType> resolveTypeSet(List<String> names, String key) {
		Set<EntityType> resolved = new HashSet<>();

		if (names != null) {
			for (String name : names) {
				NamespacedKey id = tryParse(name);
				EntityType type = id == null ? null : Registry.ENTITY_TYPE.get(id);

				if (type == null) {
					MobConduitPlugin.logger().severe(key + ": '" + name + "' is not a known entity type; ignoring");
					continue;
				}

				resolved.add(type);
			}
		}

		return Set.copyOf(resolved);
	}

	private static <E extends Enum<E>> E resolveEnum(String name, Class<E> type, String key, E fallback) {
		try {
			return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			MobConduitPlugin.logger().severe(key + ": '" + name + "' is not one of "
					+ java.util.Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT)
					+ "; falling back to " + fallback.name().toLowerCase(Locale.ROOT));
			return fallback;
		}
	}

	private static Set<String> resolveDimensions(List<String> names) {
		Set<String> resolved = new HashSet<>();

		if (names != null) {
			for (String name : names) {
				NamespacedKey id = tryParse(name);

				if (id == null) {
					MobConduitPlugin.logger().severe("disabled_dimensions: '" + name + "' is not a valid identifier; ignoring");
					continue;
				}

				resolved.add(id.toString());
			}
		}

		return resolved;
	}

	private static Material resolveBlock(String name) {
		NamespacedKey id = tryParse(name);

		if (id == null) {
			MobConduitPlugin.logger().severe("frame_block: '" + name + "' is not a valid identifier; falling back to minecraft:netherite_block");
			return Material.NETHERITE_BLOCK;
		}

		Material material = Registry.MATERIAL.get(id);

		if (material == null || !material.isBlock()) {
			MobConduitPlugin.logger().severe("frame_block: '" + name + "' is not a known block; falling back to minecraft:netherite_block");
			return Material.NETHERITE_BLOCK;
		}

		if (material.isAir()) {
			// The 42 frame positions are air by default, so an air frame block would activate
			// any crystal anywhere at full radius for free.
			MobConduitPlugin.logger().severe("frame_block: '" + name + "' is an air block; falling back to minecraft:netherite_block");
			return Material.NETHERITE_BLOCK;
		}

		return material;
	}

	/**
	 * Resolves a particle id, falling back on anything unusable.
	 *
	 * <p>Only data-less particles work here — the ones whose {@link Particle#getDataType()} is
	 * {@code Void}. Types like {@code dust}, {@code block} and {@code item} carry extra data
	 * that an id alone cannot supply, so naming one is a config error rather than something to
	 * guess at.
	 */
	private static Particle resolveParticle(String name, String key, Particle fallback) {
		NamespacedKey id = tryParse(name);

		if (id == null) {
			MobConduitPlugin.logger().severe(key + ": '" + name + "' is not a valid identifier; falling back to the default");
			return fallback;
		}

		Particle particle = Registry.PARTICLE_TYPE.get(id);

		if (particle == null) {
			MobConduitPlugin.logger().severe(key + ": '" + name + "' is not a known particle; falling back to the default");
			return fallback;
		}

		if (particle.getDataType() == Void.class) {
			return particle;
		}

		MobConduitPlugin.logger().severe(key + ": '" + name + "' needs extra data and cannot be set by id alone; falling back to the default");
		return fallback;
	}

	private static NamespacedKey tryParse(String name) {
		if (name == null) {
			return null;
		}

		return NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
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

	public Material frameBlock() {
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

	public boolean isExemptFromRemoval(EntityType type) {
		return this.resolvedExemptTypes.contains(type);
	}

	public boolean isExemptFromSuppression(EntityType type) {
		return this.resolvedSuppressExemptTypes.contains(type);
	}

	public FeedbackMode suppressionFeedback() {
		return this.resolvedSuppressionFeedback;
	}

	public RadiusShape radiusShape() {
		return this.resolvedRadiusShape;
	}

	public boolean isDimensionDisabled(World world) {
		return this.resolvedDisabledDimensions.contains(world.getKey().toString());
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

	public Particle crystalAuraParticle() {
		return this.resolvedCrystalAuraParticle;
	}

	public Particle killPlumeParticle() {
		return this.resolvedKillPlumeParticle;
	}

	public Particle killBeamParticle() {
		return this.resolvedKillBeamParticle;
	}

	public int killBeamLength() {
		return this.killBeamLength;
	}

	public Particle frameDripParticle() {
		return this.resolvedFrameDripParticle;
	}

	public Particle removalParticle() {
		return this.resolvedRemovalParticle;
	}

	public Particle removalSecondaryParticle() {
		return this.resolvedRemovalSecondaryParticle;
	}

	public Particle removalRiserParticle() {
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
