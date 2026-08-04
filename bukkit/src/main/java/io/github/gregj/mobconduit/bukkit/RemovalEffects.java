package io.github.gregj.mobconduit.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Mob;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

/**
 * The three-stage erasure a mob goes through when a conduit activates on top of it.
 *
 * <ol>
 *   <li><b>Armed</b> — a {@code minecraft:light} block appears above the mob's head.</li>
 *   <li><b>Vanished</b> — half a second later the mob is removed in a burst of soul fire.</li>
 *   <li><b>Fading</b> — the light steps down in brightness and is cleared.</li>
 * </ol>
 *
 * <p>Every stage is budgeted per tick. Lighting is the expensive part: each brightness change
 * relights up to 15 blocks in every direction, and the fade walks every level, so a mob costs
 * 15 relights. That is deliberate — it only happens on erasure, and
 * {@code max_concurrent_lights} is there if a server ever needs to bound it.
 *
 * <p>Placed lights are tracked so they can be cleared on shutdown and resumed after a chunk
 * unload. One gap remains by construction: a light fading in a chunk that is already unloaded
 * when the server stops is saved with the chunk and never cleared.
 */
public final class RemovalEffects {
	/**
	 * The fade walks every light level, 15 down to 0, which is as smooth as the block allows —
	 * brightness is an integer property with no intermediate states.
	 */
	private static final int FADE_STEPS = 15;

	private static final int LEVEL_PER_STEP = 1;

	/** Server-thread only; stands in for the level's {@code RandomSource}. */
	private static final Random RANDOM = new Random();

	/**
	 * Everything currently queued or armed. Overlapping conduits, and a forcefield interval
	 * shorter than the arm delay, would otherwise condemn the same mob repeatedly: duplicate
	 * light attempts, wasted budget, and a pendingCount that reads high. Entity does not override
	 * equals, so this is identity comparison, which is what we want.
	 */
	private final Set<Mob> inFlight = new HashSet<>();

	private final ArrayDeque<Queued> queued = new ArrayDeque<>();
	private final List<Doomed> armed = new ArrayList<>();
	private final List<FadingLight> fading = new ArrayList<>();

	private static final class Doomed {
		private final Mob mob;
		private final ConduitPos lightPos;
		private final ConduitPos sourceConduit;
		private int ticksToVanish;

		private Doomed(Mob mob, ConduitPos lightPos, ConduitPos sourceConduit, int ticksToVanish) {
			this.mob = mob;
			this.lightPos = lightPos;
			this.sourceConduit = sourceConduit;
			this.ticksToVanish = ticksToVanish;
		}
	}

	/** A queued mob paired with the conduit that condemned it, so the plume knows where to fire. */
	private record Queued(Mob mob, ConduitPos sourceConduit) {
	}

	private static final class FadingLight {
		private final ConduitPos pos;
		private int brightness;
		private int ticksToNextStep;

		private FadingLight(ConduitPos pos, int brightness, int ticksToNextStep) {
			this.pos = pos;
			this.brightness = brightness;
			this.ticksToNextStep = ticksToNextStep;
		}
	}

	public void enqueue(List<Mob> mobs, ConduitPos sourceConduit) {
		for (Mob mob : mobs) {
			if (this.inFlight.add(mob)) {
				this.queued.add(new Queued(mob, sourceConduit));
			}
		}
	}

	public boolean isIdle() {
		return this.queued.isEmpty() && this.armed.isEmpty() && this.fading.isEmpty();
	}

	public int pendingCount() {
		return this.queued.size() + this.armed.size();
	}

	public int lightCount() {
		int count = this.fading.size();

		for (Doomed doomed : this.armed) {
			if (doomed.lightPos != null) {
				count++;
			}
		}

		return count;
	}

	/** Advances all three stages by one tick. Ordered late-stage first so lights recycle promptly. */
	public void tick(World world) {
		ModConfig config = ModConfig.get();
		tickFading(world);
		tickArmed(world, config);
		arm(world, config);
	}

	private void tickFading(World world) {
		Iterator<FadingLight> it = this.fading.iterator();

		while (it.hasNext()) {
			FadingLight light = it.next();

			if (!world.isChunkLoaded(light.pos.x() >> 4, light.pos.z() >> 4)) {
				// Chunk unloaded mid-fade. Keep tracking: the light block is saved in the
				// chunk data, and the fade resumes when the chunk loads again. Dropping the
				// entry here strands an invisible light forever, which is the exact failure
				// the tracking exists to prevent.
				continue;
			}

			if (--light.ticksToNextStep > 0) {
				continue;
			}

			light.brightness -= LEVEL_PER_STEP;

			if (light.brightness <= 0) {
				clearLight(world, light.pos);
				it.remove();
				continue;
			}

			if (isOurLight(world, light.pos)) {
				world.getBlockAt(light.pos.x(), light.pos.y(), light.pos.z())
						.setBlockData(lightData(light.brightness), false);
				light.ticksToNextStep = stepLength();
			} else {
				// Someone replaced it; stop tracking rather than fight them for the block.
				it.remove();
			}
		}
	}

	private void tickArmed(World world, ModConfig config) {
		Iterator<Doomed> it = this.armed.iterator();

		while (it.hasNext()) {
			Doomed doomed = it.next();

			if (--doomed.ticksToVanish > 0) {
				continue;
			}

			it.remove();
			this.inFlight.remove(doomed.mob);
			vanish(world, config, doomed.mob, doomed.sourceConduit);

			if (doomed.lightPos != null) {
				this.fading.add(new FadingLight(doomed.lightPos, 15, stepLength()));
			}
		}
	}

	private void arm(World world, ModConfig config) {
		int budget = config.removalBudgetPerTick();

		while (budget-- > 0) {
			Queued next = this.queued.poll();

			if (next == null) {
				return;
			}

			Mob mob = next.mob();

			if (mob.isDead() || !mob.isValid()) {
				this.inFlight.remove(mob);
				continue;
			}

			ConduitPos lightPos = placeLight(world, config, mob);

			if (config.removalLightDelayTicks() <= 0) {
				this.inFlight.remove(mob);
				vanish(world, config, mob, next.sourceConduit());

				if (lightPos != null) {
					this.fading.add(new FadingLight(lightPos, 15, stepLength()));
				}
			} else {
				this.armed.add(new Doomed(mob, lightPos, next.sourceConduit(), config.removalLightDelayTicks()));
			}
		}
	}

	/** Returns the position a light was placed at, or null if none was. */
	private ConduitPos placeLight(World world, ModConfig config, Mob mob) {
		int cap = config.maxConcurrentLights();

		if (!config.removalLightEnabled() || (cap > 0 && lightCount() >= cap)) {
			return null;
		}

		int x = mob.getLocation().getBlockX();
		int y = (int) Math.floor(mob.getLocation().getY() + mob.getHeight() + 0.5);
		int z = mob.getLocation().getBlockZ();

		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			// Reading a block in an inaccessible section could sync-load the chunk on the
			// server thread.
			return null;
		}

		Block block = world.getBlockAt(x, y, z);

		// Only ever replace air, so clearing back to air later is always correct.
		if (!block.isEmpty()) {
			return null;
		}

		block.setBlockData(lightData(15), false);
		return new ConduitPos(x, y, z);
	}

	private void vanish(World world, ModConfig config, Mob mob, ConduitPos sourceConduit) {
		if (mob.isDead() || !mob.isValid()) {
			return;
		}

		// The conduit visibly answers for the kill. Gated on forcefield, where erasure is an
		// ongoing act rather than the one-off activation sweep.
		if (sourceConduit != null && config.forcefield()) {
			ConduitParticles.killPlume(world, sourceConduit);
			ConduitParticles.killBeam(world, sourceConduit);
		}

		double x = mob.getLocation().getX();
		double y = mob.getLocation().getY() + mob.getHeight() * 0.5;
		double z = mob.getLocation().getZ();
		int count = config.removalParticleCount();

		if (count > 0) {
			world.spawnParticle(config.removalParticle(), x, y, z, count, 0.35, 0.55, 0.35, 0.03, null, false);
			world.spawnParticle(config.removalSecondaryParticle(), x, y, z, Math.max(1, count / 3), 0.3, 0.45, 0.3, 0.02, null, false);
		}

		emitRisers(world, config, x, y, z);

		if (config.removalDrops()) {
			// Full death pipeline, so loot tables, XP and advancements all fire. Same call
			// /kill makes.
			mob.setHealth(0);
		} else {
			// Remove, not kill: no drops, no XP, no death event.
			mob.remove();
		}
	}

	/**
	 * Soul flames that climb instead of puffing out.
	 *
	 * <p>A particle packet with {@code count == 0} is the only way to give a particle a
	 * directed velocity: the client reads the three offset fields as a velocity vector scaled
	 * by the speed. With {@code count > 0} those fields become random offsets and the velocity
	 * is randomised in every direction, which is why the puff above cannot rise on its own.
	 *
	 * <p>One packet per riser, so this is the most expensive part of an erasure. It is bounded
	 * by {@code removal_riser_count} and only reaches players near enough to see it.
	 */
	private static void emitRisers(World world, ModConfig config, double x, double y, double z) {
		int risers = config.removalRiserCount();

		if (risers <= 0) {
			return;
		}

		for (int i = 0; i < risers; i++) {
			double driftX = (RANDOM.nextDouble() - 0.5) * 0.06;
			double driftZ = (RANDOM.nextDouble() - 0.5) * 0.06;
			double rise = config.removalRiserSpeed() * (0.75 + RANDOM.nextDouble() * 0.5);

			world.spawnParticle(config.removalRiserParticle(),
					x + (RANDOM.nextDouble() - 0.5) * 0.8,
					y + (RANDOM.nextDouble() - 0.5) * 0.6,
					z + (RANDOM.nextDouble() - 0.5) * 0.8,
					0, driftX, rise, driftZ, 1.0, null, false);
		}
	}

	private int stepLength() {
		return Math.max(1, ModConfig.get().removalLightFadeTicks() / FADE_STEPS);
	}

	private static BlockData lightData(int level) {
		Light light = (Light) Bukkit.createBlockData(Material.LIGHT);
		light.setLevel(level);
		return light;
	}

	private static boolean isOurLight(World world, ConduitPos pos) {
		// The loaded check keeps cleanup after a chunk unload from sync-loading the chunk
		// back in just to read one block. An unloaded light is already saved and out of reach.
		return world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)
				&& world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.LIGHT;
	}

	private static void clearLight(World world, ConduitPos pos) {
		if (isOurLight(world, pos)) {
			world.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
		}
	}

	/**
	 * Clears every light this world still owns. Called on shutdown and on deactivation so a
	 * stop mid-fade cannot strand invisible light blocks.
	 */
	public void clearAll(World world) {
		for (FadingLight light : this.fading) {
			clearLight(world, light.pos);
		}

		for (Doomed doomed : this.armed) {
			if (doomed.lightPos != null) {
				clearLight(world, doomed.lightPos);
			}
		}

		this.fading.clear();
		this.armed.clear();
		this.queued.clear();
		this.inFlight.clear();
	}
}
