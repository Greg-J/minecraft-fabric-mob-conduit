package io.github.gregj.mobconduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
 * relights up to 15 blocks in every direction, so the fade uses {@link #FADE_STEPS} coarse steps
 * rather than all 15 levels, and {@code max_concurrent_lights} bounds how many can be in flight.
 *
 * <p>Placed lights are tracked so they can be cleared on shutdown. Without that, stopping the
 * server mid-fade would strand invisible light blocks in the world that a player cannot find.
 */
public final class RemovalEffects {
	/**
	 * The fade walks every light level, 15 down to 0, which is as smooth as the block allows —
	 * brightness is an integer property with no intermediate states. Relighting once per level
	 * is more work than skipping levels would be, but this only runs on activation, so the cost
	 * is a one-off at a moment the player is already watching something happen.
	 */
	private static final int FADE_STEPS = LightBlock.MAX_LEVEL;

	private static final int LEVEL_PER_STEP = 1;

	private final ArrayDeque<Mob> queued = new ArrayDeque<>();
	private final List<Doomed> armed = new ArrayList<>();
	private final List<FadingLight> fading = new ArrayList<>();

	private static final class Doomed {
		private final Mob mob;
		private final BlockPos lightPos;
		private int ticksToVanish;

		private Doomed(Mob mob, BlockPos lightPos, int ticksToVanish) {
			this.mob = mob;
			this.lightPos = lightPos;
			this.ticksToVanish = ticksToVanish;
		}
	}

	private static final class FadingLight {
		private final BlockPos pos;
		private int brightness;
		private int ticksToNextStep;

		private FadingLight(BlockPos pos, int brightness, int ticksToNextStep) {
			this.pos = pos;
			this.brightness = brightness;
			this.ticksToNextStep = ticksToNextStep;
		}
	}

	public void enqueue(List<Mob> mobs) {
		this.queued.addAll(mobs);
	}

	public boolean isIdle() {
		return this.queued.isEmpty() && this.armed.isEmpty() && this.fading.isEmpty();
	}

	public int pendingCount() {
		return this.queued.size() + this.armed.size();
	}

	public int lightCount() {
		return this.armed.size() + this.fading.size();
	}

	/** Advances all three stages by one tick. Ordered late-stage first so lights recycle promptly. */
	public void tick(ServerLevel level) {
		ModConfig config = ModConfig.get();
		tickFading(level);
		tickArmed(level, config);
		arm(level, config);
	}

	private void tickFading(ServerLevel level) {
		Iterator<FadingLight> it = this.fading.iterator();

		while (it.hasNext()) {
			FadingLight light = it.next();

			if (--light.ticksToNextStep > 0) {
				continue;
			}

			light.brightness -= LEVEL_PER_STEP;

			if (light.brightness <= 0) {
				clearLight(level, light.pos);
				it.remove();
				continue;
			}

			if (isOurLight(level, light.pos)) {
				level.setBlock(light.pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light.brightness), Block.UPDATE_CLIENTS);
				light.ticksToNextStep = stepLength();
			} else {
				// Someone replaced it; stop tracking rather than fight them for the block.
				it.remove();
			}
		}
	}

	private void tickArmed(ServerLevel level, ModConfig config) {
		Iterator<Doomed> it = this.armed.iterator();

		while (it.hasNext()) {
			Doomed doomed = it.next();

			if (--doomed.ticksToVanish > 0) {
				continue;
			}

			it.remove();
			vanish(level, config, doomed.mob);

			if (doomed.lightPos != null) {
				this.fading.add(new FadingLight(doomed.lightPos, LightBlock.MAX_LEVEL, stepLength()));
			}
		}
	}

	private void arm(ServerLevel level, ModConfig config) {
		int budget = config.removalBudgetPerTick();

		while (budget-- > 0) {
			Mob mob = this.queued.poll();

			if (mob == null) {
				return;
			}

			if (mob.isRemoved()) {
				continue;
			}

			BlockPos lightPos = placeLight(level, config, mob);

			if (config.removalLightDelayTicks() <= 0) {
				vanish(level, config, mob);

				if (lightPos != null) {
					this.fading.add(new FadingLight(lightPos, LightBlock.MAX_LEVEL, stepLength()));
				}
			} else {
				this.armed.add(new Doomed(mob, lightPos, config.removalLightDelayTicks()));
			}
		}
	}

	/** Returns the position a light was placed at, or null if none was. */
	private BlockPos placeLight(ServerLevel level, ModConfig config, Mob mob) {
		int cap = config.maxConcurrentLights();

		if (!config.removalLightEnabled() || (cap > 0 && lightCount() >= cap)) {
			return null;
		}

		BlockPos pos = BlockPos.containing(mob.getX(), mob.getY() + mob.getBbHeight() + 0.5, mob.getZ());

		// Only ever replace air, so clearing back to air later is always correct.
		if (!level.isEmptyBlock(pos)) {
			return null;
		}

		level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LightBlock.MAX_LEVEL), Block.UPDATE_CLIENTS);
		return pos;
	}

	private void vanish(ServerLevel level, ModConfig config, Mob mob) {
		if (mob.isRemoved()) {
			return;
		}

		double x = mob.getX();
		double y = mob.getY() + mob.getBbHeight() * 0.5;
		double z = mob.getZ();
		int count = config.removalParticleCount();

		if (count > 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, count, 0.35, 0.55, 0.35, 0.03);
			level.sendParticles(ParticleTypes.SOUL, x, y, z, Math.max(1, count / 3), 0.3, 0.45, 0.3, 0.02);
		}

		emitRisers(level, config, x, y, z);

		if (config.removalDrops()) {
			// Full death pipeline, so loot tables, XP and advancements all fire. Same call
			// /kill makes.
			mob.kill(level);
		} else {
			// Remove, not kill: no drops, no XP, no death event.
			mob.remove(Entity.RemovalReason.DISCARDED);
		}
	}

	/**
	 * Soul flames that climb instead of puffing out.
	 *
	 * <p>A particle packet with {@code count == 0} is the only way to give a particle a directed
	 * velocity: the client reads the three distance fields as a velocity vector scaled by speed
	 * ({@code ClientPacketListener.handleParticleEvent}). With {@code count > 0} those fields
	 * become random offsets and the velocity is randomised in every direction, which is why the
	 * puff above cannot rise on its own.
	 *
	 * <p>Height comes out of the client's own physics: {@code RisingParticle} sets friction to
	 * 0.96 and a lifetime of 12-44 ticks, so an initial upward velocity of ~1.0 integrates to
	 * roughly 10 blocks for a short-lived particle and 21 for a long-lived one. The spread in
	 * lifetime is what makes the column look ragged rather than uniform.
	 *
	 * <p>One packet per riser, so this is the most expensive part of an erasure. It is bounded
	 * by {@code removal_riser_count} and only reaches players near enough to see it.
	 */
	private static void emitRisers(ServerLevel level, ModConfig config, double x, double y, double z) {
		int risers = config.removalRiserCount();

		if (risers <= 0) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < risers; i++) {
			double driftX = (random.nextDouble() - 0.5) * 0.06;
			double driftZ = (random.nextDouble() - 0.5) * 0.06;
			double rise = config.removalRiserSpeed() * (0.75 + random.nextDouble() * 0.5);

			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					x + (random.nextDouble() - 0.5) * 0.8,
					y + (random.nextDouble() - 0.5) * 0.6,
					z + (random.nextDouble() - 0.5) * 0.8,
					0, driftX, rise, driftZ, 1.0);
		}
	}

	private int stepLength() {
		return Math.max(1, ModConfig.get().removalLightFadeTicks() / FADE_STEPS);
	}

	private static boolean isOurLight(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() == Blocks.LIGHT;
	}

	private static void clearLight(ServerLevel level, BlockPos pos) {
		if (isOurLight(level, pos)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Clears every light this level still owns. Called on shutdown and on deactivation so a stop
	 * mid-fade cannot strand invisible light blocks.
	 */
	public void clearAll(ServerLevel level) {
		for (FadingLight light : this.fading) {
			clearLight(level, light.pos);
		}

		for (Doomed doomed : this.armed) {
			if (doomed.lightPos != null) {
				clearLight(level, doomed.lightPos);
			}
		}

		this.fading.clear();
		this.armed.clear();
		this.queued.clear();
	}
}
