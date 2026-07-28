package io.github.gregj.mobconduit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-level record of active conduits, plus the chunk index the spawn guard reads.
 *
 * <p>This is both the persisted state and the runtime index. Holding them on one object means
 * {@code level.getDataStorage().computeIfAbsent(TYPE)} hands back an instance that is already
 * indexed, with no second lookup and no risk of the two drifting apart.
 */
public final class ConduitStore extends SavedData {
	public static final Codec<ConduitStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Conduit.CODEC.listOf().fieldOf("conduits").forGetter(store -> List.copyOf(store.conduits))
	).apply(instance, ConduitStore::new));

	public static final SavedDataType<ConduitStore> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(MobConduit.MOD_ID, "conduits"),
			ConduitStore::new,
			CODEC,
			// SavedDataType requires a DataFixTypes and the enum has no custom entry. Vanilla
			// fixers key off their own tag paths, so this one no-ops on ours; it only matters
			// across a Minecraft version bump, and re-validation on crystal tick would rebuild
			// the state anyway.
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
	);

	/**
	 * Number of active conduits across every level. The spawn guard runs on every natural
	 * hostile spawn attempt on the server, so it checks this first: on a world with no conduits
	 * built the guard costs one static read and returns.
	 */
	private static volatile int globalActiveCount;

	/** Levels with erasure effects still in flight, so the tick hook can stay gated. */
	private static volatile int levelsWithEffects;

	private final List<Conduit> conduits = new ArrayList<>();
	private final Long2ObjectMap<Conduit[]> byChunk = new Long2ObjectOpenHashMap<>();
	private final RemovalEffects effects = new RemovalEffects();

	/** Conduit count the global counter currently reflects for this level. */
	private int countedInGlobal;

	/** Whether this level is currently counted in {@link #levelsWithEffects}. */
	private boolean effectsRegistered;

	/**
	 * Conduits that have run their one-time arming this server session. Not persisted: a restart
	 * deliberately re-arms, so mobs that accumulated while the server was down get swept.
	 */
	private final Set<BlockPos> armedThisSession = new HashSet<>();

	public ConduitStore() {
	}

	private ConduitStore(List<Conduit> loaded) {
		this.conduits.addAll(loaded);
		reindex();
	}

	public static ConduitStore get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public static boolean anyActive() {
		return globalActiveCount > 0;
	}

	public static boolean anyPendingEffects() {
		return levelsWithEffects > 0;
	}

	/** Keeps {@link #levelsWithEffects} in step with this level's pipeline on every transition. */
	private void syncEffectsFlag() {
		boolean busy = !this.effects.isIdle();

		if (busy != this.effectsRegistered) {
			levelsWithEffects += busy ? 1 : -1;
			this.effectsRegistered = busy;
		}
	}

	/**
	 * Registers or updates a conduit. Returns true when this activated a conduit that was not
	 * already active at that position, which is what triggers the one-time mob sweep.
	 */
	public boolean activate(ServerLevel level, BlockPos pos, int frameCount) {
		Conduit existing = find(pos);
		boolean isNew = existing == null;

		if (isNew || existing.frameCount() != frameCount) {
			if (existing != null) {
				this.conduits.remove(existing);
			}

			this.conduits.add(new Conduit(pos, frameCount));
			reindex();
			setDirty();
		}

		// Keyed on the session, not on isNew: after a restart a conduit loads back already
		// present, and gating the sweep on isNew meant it never ran again. Anything that piled
		// up while the server was down would sit inside the radius forever.
		if (this.armedThisSession.add(pos.immutable())) {
			ConduitSounds.activate(level, pos);
			lightBase(level, pos);
			queueRemovalSweep(level, find(pos));
		}

		return isNew;
	}

	/**
	 * Swaps the obsidian under the crystal for a light block. Only ever replaces obsidian, so
	 * {@link #restoreBase} putting obsidian back is always correct.
	 */
	private static void lightBase(ServerLevel level, BlockPos crystalPos) {
		if (!ModConfig.get().lightBaseOnActivate()) {
			return;
		}

		BlockPos base = crystalPos.below();

		if (level.getBlockState(base).getBlock() == Blocks.OBSIDIAN) {
			level.setBlock(base, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LightBlock.MAX_LEVEL), Block.UPDATE_ALL);
		}
	}

	/**
	 * Puts the obsidian back. Runs on deactivation, which is exactly when the crystal died and
	 * the player needs a placeable surface again.
	 */
	private static void restoreBase(ServerLevel level, BlockPos crystalPos) {
		BlockPos base = crystalPos.below();

		if (level.getBlockState(base).getBlock() == Blocks.LIGHT) {
			level.setBlock(base, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Forces the one-time sweep to run again for every conduit on this level. */
	public int forceSweep(ServerLevel level) {
		for (Conduit conduit : this.conduits) {
			queueRemovalSweep(level, conduit);
		}

		return this.conduits.size();
	}

	/** Re-sweeps a single conduit. Used by the opt-in forcefield mode. */
	public void sweepAt(ServerLevel level, BlockPos pos) {
		queueRemovalSweep(level, find(pos));
	}

	/** Drops a conduit. Called on crystal removal: kill, explosion, or chunk unload. */
	public boolean deactivate(ServerLevel level, BlockPos pos) {
		Conduit existing = find(pos);

		if (existing == null) {
			return false;
		}

		this.conduits.remove(existing);
		this.armedThisSession.remove(pos);
		ConduitSounds.deactivate(level, pos);
		restoreBase(level, pos);

		if (this.conduits.isEmpty()) {
			// Nothing left to erase for, and the tick hook is gated on an active conduit
			// existing. Clearing also returns any in-flight light blocks to air.
			this.effects.clearAll(level);
		}

		reindex();
		setDirty();
		return true;
	}

	/** True when a conduit is registered at exactly this position. Used to gate the ambient loop. */
	public boolean isActiveAt(BlockPos pos) {
		return find(pos) != null;
	}

	private Conduit find(BlockPos pos) {
		for (Conduit conduit : this.conduits) {
			if (conduit.pos().equals(pos)) {
				return conduit;
			}
		}

		return null;
	}

	public List<Conduit> conduits() {
		return List.copyOf(this.conduits);
	}

	/**
	 * Rebuilds the chunk index. Only runs on activation, deactivation, and config reload, never
	 * per tick or per spawn.
	 */
	private void reindex() {
		this.byChunk.clear();

		Long2ObjectMap<List<Conduit>> building = new Long2ObjectOpenHashMap<>();

		for (Conduit conduit : this.conduits) {
			if (conduit.radius() <= 0) {
				continue;
			}

			BlockPos pos = conduit.pos();
			int minChunkX = SectionPos.blockToSectionCoord(pos.getX() - conduit.radius());
			int maxChunkX = SectionPos.blockToSectionCoord(pos.getX() + conduit.radius());
			int minChunkZ = SectionPos.blockToSectionCoord(pos.getZ() - conduit.radius());
			int maxChunkZ = SectionPos.blockToSectionCoord(pos.getZ() + conduit.radius());

			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
					building.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> new ArrayList<>()).add(conduit);
				}
			}
		}

		for (Long2ObjectMap.Entry<List<Conduit>> entry : building.long2ObjectEntrySet()) {
			this.byChunk.put(entry.getLongKey(), entry.getValue().toArray(new Conduit[0]));
		}

		globalActiveCount += this.conduits.size() - this.countedInGlobal;
		this.countedInGlobal = this.conduits.size();
	}

	/**
	 * True when a hostile spawn at this position should be suppressed. One packed-long lookup
	 * rejects the common case; the exact spherical test only runs for chunks a conduit actually
	 * reaches.
	 */
	public boolean suppresses(BlockPos pos) {
		Conduit[] candidates = this.byChunk.get(ChunkPos.pack(pos));

		if (candidates == null) {
			return false;
		}

		for (Conduit conduit : candidates) {
			if (conduit.covers(pos.getX(), pos.getY(), pos.getZ())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Collects the hostiles a freshly activated conduit should erase. Gathering is one bounded
	 * query; the removal itself is spread across ticks by {@link #drainRemovals}.
	 */
	private void queueRemovalSweep(ServerLevel level, Conduit conduit) {
		if (conduit == null || conduit.radius() <= 0) {
			return;
		}

		ModConfig config = ModConfig.get();
		AABB bounds = new AABB(conduit.pos()).inflate(conduit.radius());

		List<Mob> found = level.getEntitiesOfClass(Mob.class, bounds, mob ->
				mob instanceof Enemy
						&& !mob.isRemoved()
						&& !mob.hasCustomName()
						&& !mob.isPersistenceRequired()
						&& !mob.requiresCustomPersistence()
						&& !config.isExemptFromRemoval(mob.getType())
						&& conduit.covers(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ()));

		this.effects.enqueue(found);
		syncEffectsFlag();
	}

	/**
	 * Advances the erasure pipeline: light, vanish, fade. A full-radius activation can queue
	 * hundreds of entities, and doing them all in one tick is a visible stutter.
	 */
	public void drainRemovals(ServerLevel level) {
		if (!this.effects.isIdle()) {
			this.effects.tick(level);
			syncEffectsFlag();
		}
	}

	public boolean hasPendingEffects() {
		return !this.effects.isIdle();
	}

	public int pendingRemovalCount() {
		return this.effects.pendingCount();
	}

	/**
	 * Re-derives every conduit against the current config and drops any whose frame no longer
	 * validates. Changing {@code frame_block} invalidates existing structures, and a stale
	 * active entry would suppress spawning with no visible structure causing it.
	 */
	public int revalidate(ServerLevel level) {
		ModConfig config = ModConfig.get();
		List<Conduit> survivors = new ArrayList<>();
		int dropped = 0;

		for (Conduit conduit : this.conduits) {
			BlockPos pos = conduit.pos();

			if (!level.isLoaded(pos)) {
				// Cannot read the frame; keep it and let the crystal's next tick decide.
				survivors.add(new Conduit(pos, conduit.frameCount()));
				continue;
			}

			int frameCount = FrameShape.count(level, pos, config.frameBlock());

			if (frameCount < config.frameThresholdMin()) {
				dropped++;
				continue;
			}

			survivors.add(new Conduit(pos, frameCount));
		}

		this.conduits.clear();
		this.conduits.addAll(survivors);
		reindex();
		setDirty();
		return dropped;
	}

	/**
	 * Levels are dropped wholesale on shutdown. Keeps the global counter honest and, critically,
	 * returns every in-flight light block to air so a stop mid-fade leaves nothing behind.
	 */
	public void forget(ServerLevel level) {
		// Put every swapped base back before the level goes away, so a stop while active does
		// not leave the player an invisible light block where their obsidian was.
		for (Conduit conduit : this.conduits) {
			restoreBase(level, conduit.pos());
		}

		globalActiveCount -= this.countedInGlobal;
		this.countedInGlobal = 0;
		this.armedThisSession.clear();
		this.conduits.clear();
		this.byChunk.clear();
		this.effects.clearAll(level);
		syncEffectsFlag();
	}
}
