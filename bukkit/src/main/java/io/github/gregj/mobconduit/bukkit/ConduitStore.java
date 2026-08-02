package io.github.gregj.mobconduit.bukkit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Raider;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world record of active conduits, plus the chunk index the spawn guard reads. The Bukkit
 * mirror of the Fabric mod's {@code ConduitStore} SavedData.
 *
 * <p>Bukkit has no SavedData equivalent, so persistence is a JSON file per world in the plugin
 * data folder, written on every change and read when the store is first touched. This object
 * is both the persisted state and the runtime index, same as on Fabric.
 */
public final class ConduitStore {
	private static final Gson GSON = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.setPrettyPrinting()
			.create();

	private static final Map<UUID, ConduitStore> STORES = new HashMap<>();

	/**
	 * Number of active conduits across every world. The spawn guard runs on every natural
	 * hostile spawn attempt on the server, so it checks this first: on a world with no
	 * conduits built the guard costs one static read and returns.
	 */
	private static volatile int globalActiveCount;

	/** Worlds with erasure effects still in flight, so the tick hook can stay gated. */
	private static volatile int levelsWithEffects;

	private final List<Conduit> conduits = new ArrayList<>();
	private final Map<Long, Conduit[]> byChunk = new HashMap<>();
	private final RemovalEffects effects = new RemovalEffects();

	/** Conduit count the global counter currently reflects for this world. */
	private int countedInGlobal;

	/** Whether this world is currently counted in {@link #levelsWithEffects}. */
	private boolean effectsRegistered;

	/**
	 * Conduits that have run their one-time arming this server session. Not persisted: a
	 * restart deliberately re-arms, so mobs that accumulated while the server was down get
	 * swept.
	 */
	private final Set<ConduitPos> armedThisSession = new HashSet<>();

	/**
	 * Crystal positions whose deactivation is waiting for a safe point in the tick. Parked
	 * from {@code EntityRemoveEvent} so the block work runs from the scheduler, never inside
	 * the chunk system's own update pass.
	 */
	private final List<ConduitPos> pendingDeactivations = new ArrayList<>();

	/** Last game-time each conduit fired suppression feedback; transient, self-cleaning by size. */
	private final Map<ConduitPos, Long> lastFeedback = new HashMap<>();

	/** Whether the in-memory state has changes not yet written to disk. */
	private boolean dirty;

	// --- lifecycle ----------------------------------------------------------------------

	public static ConduitStore get(World world) {
		return STORES.computeIfAbsent(world.getUID(), key -> loadFromDisk(world));
	}

	public static boolean anyActive() {
		return globalActiveCount > 0;
	}

	public static boolean anyPendingEffects() {
		return levelsWithEffects > 0;
	}

	/** Total active conduits across every world; used by the sidebar. */
	public static int totalActive() {
		return globalActiveCount;
	}

	/**
	 * Tears down and saves every store. Called from {@code onDisable}: keeps the global
	 * counter honest and, critically, returns every in-flight light block to air so a stop
	 * mid-fade leaves nothing behind.
	 */
	public static void forgetAll() {
		for (World world : Bukkit.getWorlds()) {
			ConduitStore store = STORES.get(world.getUID());

			if (store != null) {
				store.forget(world);
			}
		}

		STORES.clear();
	}

	/** Tears down and saves the store of a world that is unloading. */
	public static void onWorldUnload(World world) {
		ConduitStore store = STORES.remove(world.getUID());

		if (store != null) {
			store.forget(world);
		}
	}

	/** Keeps {@link #levelsWithEffects} in step with this world's pipeline on every transition. */
	private void syncEffectsFlag() {
		boolean busy = !this.effects.isIdle();

		if (busy != this.effectsRegistered) {
			levelsWithEffects += busy ? 1 : -1;
			this.effectsRegistered = busy;
		}
	}

	// --- persistence --------------------------------------------------------------------

	private static File storeFile(World world) {
		// World names are filesystem-friendly in practice, but nothing in the API guarantees
		// it; strip anything that could escape the folder.
		String name = world.getName().replaceAll("[^A-Za-z0-9._-]", "_");
		return new File(new File(MobConduitPlugin.instance().getDataFolder(), "worlds"), name + ".json");
	}

	private static ConduitStore loadFromDisk(World world) {
		ConduitStore store = new ConduitStore();
		File file = storeFile(world);

		if (file.isFile()) {
			try (Reader reader = Files.newBufferedReader(file.toPath())) {
				PersistedStore persisted = GSON.fromJson(reader, PersistedStore.class);

				if (persisted != null && persisted.conduits != null) {
					for (PersistedConduit persistedConduit : persisted.conduits) {
						if (persistedConduit != null && persistedConduit.pos != null) {
							store.conduits.add(new Conduit(
									new ConduitPos(persistedConduit.pos.x, persistedConduit.pos.y, persistedConduit.pos.z),
									persistedConduit.frameCount));
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				MobConduitPlugin.logger().severe("Failed to read " + file + "; starting empty: " + e);
			}
		}

		store.reindex();
		return store;
	}

	/** Writes the conduit list. Runs on every state change; changes are rare. */
	private void save(World world) {
		if (!this.dirty) {
			return;
		}

		this.dirty = false;
		File file = storeFile(world);
		PersistedStore persisted = new PersistedStore();
		persisted.conduits = new ArrayList<>();

		for (Conduit conduit : this.conduits) {
			PersistedConduit persistedConduit = new PersistedConduit();
			PersistedPos pos = new PersistedPos();
			pos.x = conduit.pos().x();
			pos.y = conduit.pos().y();
			pos.z = conduit.pos().z();
			persistedConduit.pos = pos;
			persistedConduit.frameCount = conduit.frameCount();
			persisted.conduits.add(persistedConduit);
		}

		try {
			File parent = file.getParentFile();

			if (parent != null) {
				Files.createDirectories(parent.toPath());
			}

			try (Writer writer = Files.newBufferedWriter(file.toPath())) {
				GSON.toJson(persisted, writer);
			}
		} catch (IOException e) {
			MobConduitPlugin.logger().severe("Failed to write " + file + ": " + e);
		}
	}

	@SuppressWarnings("unused") // Gson field layout, mirrors the Fabric codec's JSON shape.
	private static class PersistedStore {
		List<PersistedConduit> conduits;
	}

	@SuppressWarnings("unused")
	private static class PersistedConduit {
		PersistedPos pos;
		int frameCount;
	}

	@SuppressWarnings("unused")
	private static class PersistedPos {
		int x;
		int y;
		int z;
	}

	// --- activation ---------------------------------------------------------------------

	/**
	 * Registers or updates a conduit, running the one-time arming (sound, base light, sweep)
	 * the first time each position activates in a server session.
	 */
	public void activate(World world, ConduitPos pos, int frameCount) {
		Conduit existing = find(pos);
		boolean changed = existing == null || existing.frameCount() != frameCount;

		if (changed) {
			if (existing != null) {
				this.conduits.remove(existing);
			}

			this.conduits.add(new Conduit(pos, frameCount));
			reindex();
			this.dirty = true;
			save(world);
		}

		// Keyed on the session, not on isNew: after a restart a conduit loads back already
		// present, and gating the sweep on isNew meant it never ran again. Anything that piled
		// up while the server was down would sit inside the radius forever.
		boolean armedNow = this.armedThisSession.add(pos);

		if (armedNow) {
			ConduitSounds.activate(world, pos);
			lightBase(world, pos);
			queueRemovalSweep(world, find(pos));
		}

		if (changed || armedNow) {
			// Chunk reload and restart both arrive here, and both can meet a hologram restored
			// from disk; show() dedups, so exactly one survives.
			Holograms.show(world, find(pos));
		}
	}

	/**
	 * Swaps the obsidian under the crystal for a light block. Only ever replaces obsidian, so
	 * {@link #restoreBase} putting obsidian back is always correct.
	 */
	private static void lightBase(World world, ConduitPos crystalPos) {
		if (!ModConfig.get().lightBaseOnActivate()) {
			return;
		}

		ConduitPos base = crystalPos.below();

		if (world.getBlockAt(base.x(), base.y(), base.z()).getType() == Material.OBSIDIAN) {
			Light light = (Light) Bukkit.createBlockData(Material.LIGHT);
			light.setLevel(15);
			world.getBlockAt(base.x(), base.y(), base.z()).setBlockData(light);
		}
	}

	/**
	 * Puts the obsidian back. Runs on deactivation, which is exactly when the crystal died and
	 * the player needs a placeable surface again.
	 */
	private static void restoreBase(World world, ConduitPos crystalPos) {
		ConduitPos base = crystalPos.below();

		// A deactivation drained after the crystal's chunk unloaded must not force the chunk
		// back in just to swap a block. The light stays in the saved chunk, and lightBase()
		// only ever swapping obsidian keeps the pairing consistent when it reloads.
		if (!world.isChunkLoaded(base.x() >> 4, base.z() >> 4)) {
			return;
		}

		if (world.getBlockAt(base.x(), base.y(), base.z()).getType() == Material.LIGHT) {
			world.getBlockAt(base.x(), base.y(), base.z()).setType(Material.OBSIDIAN);
		}
	}

	/** Forces the one-time sweep to run again for every conduit on this world. */
	public int forceSweep(World world) {
		for (Conduit conduit : this.conduits) {
			queueRemovalSweep(world, conduit);
		}

		return this.conduits.size();
	}

	/** Re-sweeps a single conduit. Used by the opt-in forcefield mode. */
	public void sweepAt(World world, ConduitPos pos) {
		queueRemovalSweep(world, find(pos));
	}

	/** Parks a deactivation for {@link #drainDeactivations}; safe to call from entity events. */
	public void deferDeactivate(ConduitPos pos) {
		this.pendingDeactivations.add(pos);
	}

	/** Runs parked deactivations. Called from the scheduler, never inside chunk updates. */
	public void drainDeactivations(World world) {
		if (this.pendingDeactivations.isEmpty()) {
			return;
		}

		List<ConduitPos> pending = List.copyOf(this.pendingDeactivations);
		this.pendingDeactivations.clear();

		for (ConduitPos pos : pending) {
			deactivate(world, pos);
		}
	}

	/** Drops a conduit. Called on crystal removal: kill, explosion, or chunk unload. */
	public boolean deactivate(World world, ConduitPos pos) {
		Conduit existing = find(pos);

		if (existing == null) {
			return false;
		}

		this.conduits.remove(existing);
		this.armedThisSession.remove(pos);
		ConduitSounds.deactivate(world, pos);
		restoreBase(world, pos);
		Holograms.remove(world, pos);

		if (this.conduits.isEmpty()) {
			// Nothing left to erase for, and the tick hook is gated on an active conduit
			// existing. Clearing also returns any in-flight light blocks to air.
			this.effects.clearAll(world);
		}

		syncEffectsFlag();
		reindex();
		this.dirty = true;
		save(world);
		return true;
	}

	/** True when a conduit is registered at exactly this position. Used to gate the ambient loop. */
	public boolean isActiveAt(ConduitPos pos) {
		return find(pos) != null;
	}

	private Conduit find(ConduitPos pos) {
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
	 * Rebuilds the chunk index. Only runs on activation, deactivation, and config reload,
	 * never per tick or per spawn.
	 */
	private void reindex() {
		this.byChunk.clear();
		Map<Long, List<Conduit>> building = new HashMap<>();

		for (Conduit conduit : this.conduits) {
			if (conduit.radius() <= 0) {
				continue;
			}

			ConduitPos pos = conduit.pos();
			int minChunkX = (pos.x() - conduit.radius()) >> 4;
			int maxChunkX = (pos.x() + conduit.radius()) >> 4;
			int minChunkZ = (pos.z() - conduit.radius()) >> 4;
			int maxChunkZ = (pos.z() + conduit.radius()) >> 4;

			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
					building.computeIfAbsent(packChunk(chunkX, chunkZ), key -> new ArrayList<>()).add(conduit);
				}
			}
		}

		for (Map.Entry<Long, List<Conduit>> entry : building.entrySet()) {
			this.byChunk.put(entry.getKey(), entry.getValue().toArray(new Conduit[0]));
		}

		globalActiveCount += this.conduits.size() - this.countedInGlobal;
		this.countedInGlobal = this.conduits.size();
	}

	private static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
	}

	/**
	 * The conduit suppressing this position, or null. One packed-long lookup rejects the
	 * common case; the exact spherical test only runs for chunks a conduit actually reaches.
	 */
	public Conduit suppressingConduit(ConduitPos pos) {
		Conduit[] candidates = this.byChunk.get(packChunk(pos.x() >> 4, pos.z() >> 4));

		if (candidates == null) {
			return null;
		}

		for (Conduit conduit : candidates) {
			if (conduit.covers(pos.x(), pos.y(), pos.z())) {
				return conduit;
			}
		}

		return null;
	}

	/**
	 * Rate-limits suppression feedback to one message per conduit per cooldown. Returns true —
	 * and records the time — when a feedback may fire now.
	 */
	public boolean markFeedback(ConduitPos pos, long gameTime, int cooldownTicks) {
		Long last = this.lastFeedback.get(pos);

		if (last != null && gameTime - last < cooldownTicks) {
			return false;
		}

		this.lastFeedback.put(pos, gameTime);
		return true;
	}

	/**
	 * Collects the hostiles a freshly activated conduit should erase. Gathering is one bounded
	 * query; the removal itself is spread across ticks by {@link #drainRemovals}.
	 */
	private void queueRemovalSweep(World world, Conduit conduit) {
		if (conduit == null || conduit.radius() <= 0) {
			return;
		}

		ModConfig config = ModConfig.get();
		double radius = conduit.radius() + 1.0;
		Location center;
		double rangeX;
		double rangeY;
		double rangeZ;

		if (conduit.cylindrical()) {
			// A cylinder reaches the whole column; querying the sphere's box would scan entity
			// sections that can never match the covers() test.
			double minY = world.getMinHeight();
			double maxY = world.getMaxHeight();
			center = new Location(world, conduit.pos().centerX(), (minY + maxY) / 2.0, conduit.pos().centerZ());
			rangeX = radius;
			rangeY = (maxY - minY) / 2.0;
			rangeZ = radius;
		} else {
			center = new Location(world, conduit.pos().centerX(), conduit.pos().centerY(), conduit.pos().centerZ());
			rangeX = radius;
			rangeY = radius;
			rangeZ = radius;
		}

		List<Mob> found = new ArrayList<>();

		for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, rangeX, rangeY, rangeZ,
				entity -> entity instanceof Mob mob
						&& Hostiles.isSuppressible(mob)
						&& !mob.isDead()
						&& !config.isExemptFromSuppression(mob.getType())
						&& !isProtected(config, mob)
						&& conduit.covers(mob.getLocation().getBlockX(), mob.getLocation().getBlockY(), mob.getLocation().getBlockZ()))) {
			found.add((Mob) entity);
		}

		this.effects.enqueue(found, conduit.pos());
		syncEffectsFlag();
	}

	/**
	 * Exemptions from erasure: named, persistence-flagged, leashed, tamed, raid members,
	 * config-exempt types, and anything whose spawn was player-driven.
	 *
	 * <p>Bukkit has no equivalent of vanilla's {@code requiresCustomPersistence()}, but the
	 * two vanilla overrides that method stands for are checked directly — raid membership
	 * ({@code Raider#getRaid}; raids are player-triggered and the conduit stays out of them)
	 * and tamed horses covering the undead mounts the sweep targets — and its base behaviour
	 * (passenger-or-leashed) is already covered by the leashed check: riding on its own
	 * exempts nothing, because vanilla now spawns mounted hostiles naturally. Endermen never
	 * reach this method at all — they are neutral and the sweep filter never selects them,
	 * see {@link Hostiles}.
	 *
	 * <p>The spawn-reason check is what keeps "mob farms inside the radius keep working" true
	 * under {@code forcefield}: spawner and breeding output would otherwise be erased within
	 * one interval. Spawn-egg and {@code /summon} hostiles are deliberately fair game — they
	 * spawn fine, then get swept like anything else standing in the radius. A null reason is
	 * fair game too: it means disk-loaded (records do not survive a restart, and the arming
	 * sweep exists precisely to clear what accumulated while the server was down — name-tag
	 * pen stock to keep it).
	 */
	private static boolean isProtected(ModConfig config, Mob mob) {
		if (mob.getCustomName() != null
				|| !mob.getRemoveWhenFarAway()
				|| mob.isLeashed()
				|| config.isExemptFromRemoval(mob.getType())) {
			return true;
		}

		if (mob instanceof Raider raider && raider.getRaid() != null) {
			return true;
		}

		if (mob instanceof AbstractHorse horse && horse.isTamed()) {
			return true;
		}

		return SpawnOrigins.sweepExempt(SpawnOrigins.effectiveReason(mob));
	}

	/**
	 * Advances the erasure pipeline: light, vanish, fade. A full-radius activation can queue
	 * hundreds of entities, and doing them all in one tick is a visible stutter.
	 */
	public void drainRemovals(World world) {
		if (!this.effects.isIdle()) {
			this.effects.tick(world);
			syncEffectsFlag();
		}
	}

	public int pendingRemovalCount() {
		return this.effects.pendingCount();
	}

	/**
	 * Re-derives every conduit against the current config and drops any whose frame no longer
	 * validates. Changing {@code frame_block} invalidates existing structures, and a stale
	 * active entry would suppress spawning with no visible structure causing it.
	 */
	public int revalidate(World world) {
		ModConfig config = ModConfig.get();
		boolean dimDisabled = config.isDimensionDisabled(world);
		List<Conduit> survivors = new ArrayList<>();
		int dropped = 0;

		for (Conduit conduit : this.conduits) {
			ConduitPos pos = conduit.pos();

			if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
				if (dimDisabled) {
					// A crystal here can never re-activate, so keeping the entry would park a
					// suppression zone nothing can see or remove.
					this.armedThisSession.remove(pos);
					dropped++;
					continue;
				}

				// Cannot read the frame; keep it and let the crystal's next validation decide.
				survivors.add(new Conduit(pos, conduit.frameCount()));
				continue;
			}

			if (dimDisabled) {
				restoreBase(world, pos);
				Holograms.remove(world, pos);
				this.armedThisSession.remove(pos);
				dropped++;
				continue;
			}

			if (world.getNearbyEntities(
					new Location(world, pos.centerX(), pos.centerY(), pos.centerZ()), 0.5, 0.5, 0.5,
					entity -> entity instanceof EnderCrystal).isEmpty()) {
				// The frame is intact but the crystal is gone — teleported, data-edited, or
				// removed by another plugin — so nothing will ever validate this conduit again.
				restoreBase(world, pos);
				Holograms.remove(world, pos);
				this.armedThisSession.remove(pos);
				dropped++;
				continue;
			}

			int frameCount = FrameShape.count(world, pos, config.frameBlock());

			if (frameCount < config.frameThresholdMin()) {
				// Same teardown as deactivate: without it a frame_block change strands the
				// light block that replaced the obsidian, and a rebuild re-activates silently
				// because the position is still in armedThisSession.
				restoreBase(world, pos);
				Holograms.remove(world, pos);
				this.armedThisSession.remove(pos);
				dropped++;
				continue;
			}

			Conduit survivor = new Conduit(pos, frameCount);
			survivors.add(survivor);
			// A radius edit re-derives the conduit without an activation, so the hologram's
			// text would go stale; unloaded survivors self-heal on the crystal's next validation.
			Holograms.refresh(world, survivor);
		}

		this.conduits.clear();
		this.conduits.addAll(survivors);
		reindex();
		this.dirty = true;
		save(world);
		return dropped;
	}

	/**
	 * Drops the in-memory state for a world that is going away. Keeps the global counter
	 * honest and, critically, returns every in-flight light block to air so a stop mid-fade
	 * leaves nothing behind. The conduit list is saved first: a stop while active must reload
	 * the conduits on the next boot.
	 */
	public void forget(World world) {
		save(world);

		// Put every swapped base back before the world goes away, so a stop while active does
		// not leave the player an invisible light block where their obsidian was.
		for (Conduit conduit : this.conduits) {
			restoreBase(world, conduit.pos());
		}

		globalActiveCount -= this.countedInGlobal;
		this.countedInGlobal = 0;
		this.armedThisSession.clear();
		this.pendingDeactivations.clear();
		this.conduits.clear();
		this.byChunk.clear();
		this.effects.clearAll(world);
		syncEffectsFlag();
	}
}
