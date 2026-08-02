# Bug-fix Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the seven verified bugs and ride-along nits from `docs/superpowers/specs/2026-07-29-bugfix-batch-design.md`, then bring docs back in line with the code.

**Architecture:** Surgical edits to five existing classes plus one new tiny class (`Hostiles`). No new dependencies, no new mixins, no config-key additions. The project has no unit-test harness and adding one is a dependency decision outside this batch's scope, so verification is `./gradlew build` plus a dev-server smoke test.

**Tech Stack:** Java 25, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Minecraft 26.2 (Mojang mappings — verify every name against `./.minecraft-src/`, never memory).

## Global Constraints

- Minecraft names only from `./.minecraft-src/` (26.2 snapshot), cited by path in comments where the mod already does so.
- Server-side only; nothing may require a client mod. No new registry entries.
- Mixin bodies stay single-call; no new mixins in this batch.
- Performance: no per-tick scans added anywhere; `onCrystalTick` stays throttled.
- Commit messages: one line, imperative, lowercase.
- Commits require explicit user confirmation before any `git commit` runs — pause and ask before the first commit step.

---

### Task 1: `Hostiles` predicate — neutral mobs unaffected (bug #7, counter nit)

**Files:**
- Create: `src/main/java/io/github/gregj/mobconduit/Hostiles.java`
- Modify: `src/main/java/io/github/gregj/mobconduit/MobConduit.java:77-90`
- Modify: `src/main/java/io/github/gregj/mobconduit/ConduitStore.java:325-329,361-386`

**Interfaces:**
- Produces: `Hostiles.isSuppressible(net.minecraft.world.entity.Entity) -> boolean` — consumed by `MobConduit.allowSpawn` and `ConduitStore.queueRemovalSweep`.

Background, verified against `.minecraft-src`: `NeutralMob` implementers in 26.2 are exactly `EnderMan`, `ZombifiedPiglin`, `Wolf`, `IronGolem`, `PolarBear`, `Bee` (grep `NeutralMob` under `net/minecraft/world/entity/`). `Spider` (`spider/Spider.java:48`) and `AbstractPiglin` (`piglin/AbstractPiglin.java:25`) do **not** implement it, so spiders and piglins stay suppressed; only endermen and zombified piglins change behavior, matching the AGENTS.md rule "passive and neutral spawns are unaffected."

- [ ] **Step 1: Create `Hostiles.java`**

```java
package io.github.gregj.mobconduit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * What the conduit suppresses: hostile mobs, in the spec's sense — {@code Enemy} minus
 * {@code NeutralMob}, plus the undead mounts.
 *
 * <p>{@code Enemy} alone catches endermen and zombified piglins, which are neutral until
 * provoked; the spec leaves neutral spawns alone, so they stay spawnable and unswept. In 26.2
 * they are the only two {@code Enemy & NeutralMob} types (wolf, iron golem, polar bear and bee
 * implement {@code NeutralMob} but are not {@code Enemy}; spiders and piglins are {@code Enemy}
 * without {@code NeutralMob} and stay suppressed).
 *
 * <p>{@code Enemy} alone also misses vanilla's mounted spawns: a zombie horse is
 * {@code MobCategory.MONSTER} but extends {@code AbstractHorse}, and vetoing only its rider
 * would leave riderless undead mounts accumulating inside the radius. An explicit mount set
 * rather than the MONSTER category, because the category also holds the sulfur cube — passive
 * and farmable.
 *
 * <p>One predicate, used by both the spawn guard and the erasure sweep, so the two can never
 * drift: anything the guard would veto, the sweep would erase, and vice versa.
 */
public final class Hostiles {
	private Hostiles() {
	}

	public static boolean isSuppressible(Entity entity) {
		return (entity instanceof Enemy || SpawnOrigin.UNDEAD_MOUNTS.contains(entity.getType()))
				&& !(entity instanceof NeutralMob);
	}
}
```

- [ ] **Step 2: Rewire the spawn guard in `MobConduit.allowSpawn`**

Replace the counting branch (currently `MobConduit.java:77-79`):

```java
			if (entity instanceof Enemy) {
				SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
			}
```

with:

```java
			if (Hostiles.isSuppressible(entity)) {
				SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
			}
```

Replace the hostile check (currently `MobConduit.java:84-90`, including its comment) with:

```java
		if (!Hostiles.isSuppressible(entity)) {
			return true;
		}
```

(The comment moves to `Hostiles`, which now owns the rule.)

In the trap-horse veto (currently `MobConduit.java:67-72`), add the missing counter so the sidebar stays consistent:

```java
			if (entity instanceof SkeletonHorse horse && horse.isTrap()
					&& ConduitStore.anyActive()
					&& ConduitStore.get(level).suppresses(entity.blockPosition())) {
				SpawnStats.HOSTILE_OTHER_REASON.incrementAndGet();
				SpawnStats.SUPPRESSED.incrementAndGet();
				return vetoSpawn(entity);
			}
```

Remove the now-unused `import net.minecraft.world.entity.monster.Enemy;` from `MobConduit.java`.

- [ ] **Step 3: Rewire the sweep filter in `ConduitStore.queueRemovalSweep`**

Replace the filter predicate (currently `ConduitStore.java:325-329`):

```java
	List<Mob> found = level.getEntitiesOfClass(Mob.class, bounds, mob ->
			(mob instanceof Enemy || SpawnOrigin.UNDEAD_MOUNTS.contains(mob.getType()))
					&& !mob.isRemoved()
					&& !isProtected(config, mob)
					&& conduit.covers(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ()));
```

with:

```java
	List<Mob> found = level.getEntitiesOfClass(Mob.class, bounds, mob ->
			Hostiles.isSuppressible(mob)
					&& !mob.isRemoved()
					&& !isProtected(config, mob)
					&& conduit.covers(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ()));
```

- [ ] **Step 4: Drop the dead enderman exemption in `ConduitStore.isProtected`**

Endermen are neutral and no longer reach the sweep at all, so the carried-block branch can
never fire. Delete from `isProtected`:

```java
		if (mob instanceof EnderMan enderMan && enderMan.getCarriedBlock() != null) {
			return true;
		}
```

Remove `import net.minecraft.world.entity.monster.EnderMan;` and
`import net.minecraft.world.entity.monster.Enemy;` from `ConduitStore.java`. Update the
`isProtected` javadoc: drop "carrying-a-block" from the first sentence, and in the long
paragraph replace the clause "and an enderman's carried block ({@code EnderMan.java:393-395})"
with a note that endermen no longer reach this method because they are neutral — see
{@link Hostiles}. Update the sweep-exemption sentence in {@link Hostiles}' favor.

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL, no unused-import warnings.

- [ ] **Step 6: Commit (after user confirmation)**

```bash
git add src/main/java/io/github/gregj/mobconduit/Hostiles.java src/main/java/io/github/gregj/mobconduit/MobConduit.java src/main/java/io/github/gregj/mobconduit/ConduitStore.java
git commit -m "leave neutral mobs alone: endermen and zombified piglins stay spawnable"
```

---

### Task 2: `ConduitStore` lifecycle fixes (bugs #1, #5, #6b; dead code)

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/ConduitStore.java:116-144,215-236,399-401,407-441`
- Modify: `src/main/java/io/github/gregj/mobconduit/ConduitDetector.java:79-89` (caller of `activate`)

**Interfaces:**
- Consumes: `Hostiles.isSuppressible` (Task 1).
- Produces: `ConduitStore.activate(...)` becomes `void`; `hasPendingEffects()` is deleted (no callers — verified by audit, re-check with grep before deleting).

- [ ] **Step 1: `revalidate` teardown for dropped conduits (bug #1)**

In `ConduitStore.revalidate`, replace the drop branch (currently `:428-431`):

```java
			if (frameCount < config.frameThresholdMin()) {
				dropped++;
				continue;
			}
```

with:

```java
			if (frameCount < config.frameThresholdMin()) {
				// Same teardown as deactivate: without it a frame_block change strands the
				// light block that replaced the obsidian, and a rebuild re-activates silently
				// because the position is still in armedThisSession.
				restoreBase(level, pos);
				this.armedThisSession.remove(pos);
				dropped++;
				continue;
			}
```

- [ ] **Step 2: `deactivate` re-syncs the effects flag (bug #5)**

In `ConduitStore.deactivate`, after the `if (this.conduits.isEmpty()) { ... }` block and before `reindex();`, add:

```java
		syncEffectsFlag();
```

- [ ] **Step 3: `revalidate` checks the crystal still exists (bug #6b)**

In `ConduitStore.revalidate`, in the loaded branch just before `int frameCount = FrameShape.count(...)`, add:

```java
			if (level.getEntitiesOfClass(EndCrystal.class, new AABB(pos)).isEmpty()) {
				// The frame is intact but the crystal is gone — teleported, data-edited, or
				// removed by another mod — so nothing will ever tick this conduit again.
				restoreBase(level, pos);
				this.armedThisSession.remove(pos);
				dropped++;
				continue;
			}
```

Add `import net.minecraft.world.entity.boss.enderdragon.EndCrystal;`. (`AABB` is already imported; `getEntitiesOfClass` is already used at `:325`.)

- [ ] **Step 4: `activate` returns void; delete `hasPendingEffects`**

Change the signature and javadoc of `activate` (currently `:116-120`):

```java
	/** Registers or updates a conduit, running the one-time arming on first activation each session. */
	public void activate(ServerLevel level, BlockPos pos, int frameCount) {
```

Keep `boolean isNew = existing == null;` for the add logic but delete `return isNew;`. The only caller (`ConduitDetector.validate`, `:85`) already ignores the result — no caller change needed beyond recompiling. Delete the `hasPendingEffects()` method (`:399-401`); confirm zero callers first:

Run: `grep -rn "hasPendingEffects" src/main/java` — expect only the definition.

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit (after user confirmation)**

```bash
git add src/main/java/io/github/gregj/mobconduit/ConduitStore.java src/main/java/io/github/gregj/mobconduit/ConduitDetector.java
git commit -m "teardown dropped conduits and re-sync the effects flag"
```

---

### Task 3: Crystal teleport tracking (bug #6a)

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/ConduitDetector.java`

**Interfaces:**
- Consumes: `ConduitStore.deactivate(ServerLevel, BlockPos)` (existing).

- [ ] **Step 1: Track crystal positions, deactivate the old spot**

Add to `ConduitDetector`:

```java
	/**
	 * Last position each crystal was validated at, so a same-level move (a teleport fires no
	 * ENTITY_UNLOAD) deactivates the conduit it left behind instead of leaving a crystal-less
	 * suppression zone that persists forever. Weak keys: entries die with the crystal. Crystal
	 * ticks run on the server thread, so no synchronization.
	 */
	private static final java.util.WeakHashMap<EndCrystal, BlockPos> LAST_VALIDATED_AT = new java.util.WeakHashMap<>();
```

In `onCrystalTick`, inside the `if (due)` block, replace `validate(level, pos);` with:

```java
		if (due) {
			BlockPos previous = LAST_VALIDATED_AT.put(crystal, pos);

			if (previous != null && !previous.equals(pos)) {
				ConduitStore.get(level).deactivate(level, previous);
			}

			validate(level, pos);
		}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (after user confirmation)**

```bash
git add src/main/java/io/github/gregj/mobconduit/ConduitDetector.java
git commit -m "deactivate the conduit a teleported crystal leaves behind"
```

---

### Task 4: `RemovalEffects` light-block fixes (bugs #3, #4; two nits)

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/RemovalEffects.java:30-33,93-95,105-131,183-199`

**Interfaces:**
- Produces: no API changes; `lightCount()` semantics change to "lights actually in flight."

- [ ] **Step 1: Keep fading entries across chunk unload (bug #3)**

Rewrite `tickFading` (currently `:105-131`) as:

```java
	private void tickFading(ServerLevel level) {
		Iterator<FadingLight> it = this.fading.iterator();

		while (it.hasNext()) {
			FadingLight light = it.next();

			if (!level.isLoaded(light.pos)) {
				// Chunk unloaded mid-fade. Keep tracking: the light block is saved in the
				// chunk data, and the fade resumes when the chunk loads again. Dropping the
				// entry here strands an invisible light forever, which is the exact failure
				// the tracking exists to prevent. Retention is bounded in practice — entries
				// die when the chunk reloads or on clearAll.
				continue;
			}

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
```

Note the countdown no longer burns while the chunk is unloaded, and `isOurLight`'s own
`isLoaded` check is now redundant on this path but stays as `clearLight`'s guard.

Update the class javadoc (`:30-33`) — replace the last paragraph with:

```
 * <p>Placed lights are tracked so they can be cleared on shutdown and resumed after a chunk
 * unload. One gap remains by construction: a light fading in a chunk that is already unloaded
 * when the server stops is saved with the chunk and never cleared.
```

- [ ] **Step 2: `isLoaded` guard in `placeLight` (bug #4)**

At the top of `placeLight`, before the `isEmptyBlock` read (currently `:193`), add:

```java
		if (!level.isLoaded(pos)) {
			// getBlockState on a non-FULL chunk sync-loads it on the server thread; the sweep
			// can reach mobs whose head block sits in an inaccessible section.
			return null;
		}
```

(Move the `BlockPos pos = ...` line above the guard so `pos` exists; it already does at `:190`.)

- [ ] **Step 3: `lightCount` counts real lights only (nit)**

Replace `lightCount()` (`:93-95`) with:

```java
	public int lightCount() {
		int count = this.fading.size();

		for (Doomed doomed : this.armed) {
			if (doomed.lightPos != null) {
				count++;
			}
		}

		return count;
	}
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (after user confirmation)**

```bash
git add src/main/java/io/github/gregj/mobconduit/RemovalEffects.java
git commit -m "stop stranding erasure light blocks on chunk unload"
```

---

### Task 5: `ModConfig` validation fixes (bug #2; two nits)

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/ModConfig.java:70,149,316,333,354-368`

**Interfaces:**
- Produces: no signature changes; `crystal_aura_particle` default changes to `minecraft:trial_spawner_detection_ominous` (fresh configs only; existing files keep their written value).

- [ ] **Step 1: Reject air frame blocks (bug #2)**

In `resolveBlock` (`:354-368`), replace the final return:

```java
		return BuiltInRegistries.BLOCK.getValue(id);
```

with:

```java
		Block block = BuiltInRegistries.BLOCK.getValue(id);

		if (block.defaultBlockState().isAir()) {
			// The 42 frame positions are air by default, so an air frame block would activate
			// any crystal anywhere at full radius for free.
			MobConduit.LOGGER.error("frame_block: '{}' is an air block; falling back to minecraft:netherite_block", name);
			return Blocks.NETHERITE_BLOCK;
		}

		return block;
```

- [ ] **Step 2: Clamp `removal_light_fade_ticks` to ≥ 15 (nit)**

In `validate()` (`:333`), change:

```java
		this.removalLightFadeTicks = clamp(this.removalLightFadeTicks, 1, 600);
```

to:

```java
		// The fade walks one light level per step, so anything under 15 ticks collapses to 15
		// anyway; say so in the clamp rather than silently misbehaving.
		this.removalLightFadeTicks = clamp(this.removalLightFadeTicks, 15, 600);
```

- [ ] **Step 3: Make the aura default match its documented intent (nit)**

`ConduitParticles.crystalAura`'s javadoc argues the aura works as a permanent marker because
`trial_spawner_detection_ominous` is registered with `overrideLimiter = true`
(`ParticleTypes.java:156`: `TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS = register("trial_spawner_detection_ominous", true)`)
— but the configured default `sculk_soul` is registered `false` (`:76`) and gets distance- and
setting-culled. Change the default to the particle the comment describes:

- `ModConfig.java:70`: `private String crystalAuraParticle = "minecraft:trial_spawner_detection_ominous";`
- `ModConfig.java:149`: `private transient SimpleParticleType resolvedCrystalAuraParticle = ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS;`
- `ModConfig.java:316`: fallback argument becomes `ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS`.
- `ModConfig.java:77` comment on `crystalAuraEnabled` already names the particle — verify it still reads correctly.

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (after user confirmation)**

```bash
git add src/main/java/io/github/gregj/mobconduit/ModConfig.java
git commit -m "reject air frame blocks and fix aura default visibility"
```

---

### Task 6: Dead code and stale javadocs

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/SpawnStats.java:40-46`
- Modify: `src/main/java/io/github/gregj/mobconduit/MobConduit.java:186-188`
- Modify: `src/main/java/io/github/gregj/mobconduit/mixin/EndCrystalMixin.java:10-14`
- Modify: `src/main/java/io/github/gregj/mobconduit/MobConduitCommand.java:20-21`

- [ ] **Step 1: Verify zero callers, then delete**

Run: `grep -rn "SpawnStats.reset\|\.id(\|MobConduit\.id" src/main/java`
Expected: no callers of `reset()` or `id()` outside their definitions.

Delete `SpawnStats.reset()` and `MobConduit.id()`. Check whether `Identifier` is still used in `MobConduit.java` after deleting `id()` (it isn't — `ConduitStore.TYPE` builds its own) and remove the import if unused.

- [ ] **Step 2: Fix `EndCrystalMixin` javadoc**

Replace "The only Mixin in the mod." with "One of two mixins in the mod (the other backfills spawn reasons in {@code MobMixin})."

- [ ] **Step 3: Fix `MobConduitCommand` class javadoc**

Read the current javadoc at `:20-21` and rewrite it to name all six subcommands: `reload`, `status` / `status off`, `sweep`, `set`, `get`, `build`.

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit (after user confirmation)**

```bash
git add -A src/main/java
git commit -m "drop dead code and stale javadocs"
```

---

### Task 7: Documentation catch-up (AGENTS.md, README.md, MODRINTH.md)

**Files:**
- Modify: `AGENTS.md` (config table, "Hostile mobs only" bullet, known-behavior notes)
- Modify: `README.md` (commands table, mixin count, exempt-types default, jar version)
- Modify: `MODRINTH.md` (commands list)

- [ ] **Step 1: AGENTS.md config table**

Add the 18 implemented-but-undocumented keys, sourced from `ModConfig.java:53-142` (defaults in parens): `removal_exempt_types` (wither, ender_dragon, warden, elder_guardian), `removal_budget_per_tick` (32), `removal_light_enabled` (true), `crystal_aura_particle` (trial_spawner_detection_ominous), `kill_plume_particle` (sculk_soul), `kill_beam_particle` (sonic_boom), `frame_drip_particle` (dripping_obsidian_tear), `removal_particle` (soul_fire_flame), `removal_secondary_particle` (soul), `removal_riser_particle` (soul_fire_flame), `crystal_aura_enabled`/`_count`/`_interval_ticks` (true/6/4), `kill_plume_count` (0), `kill_beam_length` (0), `frame_drips_enabled`/`_count`/`_interval_ticks` (true/3/8).

- [ ] **Step 2: AGENTS.md hostile rule**

Rewrite the "**Hostile mobs only.** Passive and neutral spawns are unaffected." bullet to state the implemented rule precisely: suppression targets `Enemy` minus `NeutralMob` (endermen and zombified piglins are the only two affected types) plus the three undead mounts, via the shared `Hostiles.isSuppressible` predicate used by both the guard and the sweep.

- [ ] **Step 3: AGENTS.md known behaviors**

Add a short "Known behaviors" list: vetoed spawns still consume mob-cap credits (inherent to `ALLOW_LOAD`; brief spawn throttling outside the radius after a mass erasure); `SpawnOrigin` records die on chunk unload, so chunk-cycled spawner-farm output inside the radius can be swept; with `forcefield: false`, reinforcements / village sieges / nether-portal piglins are not suppressed; a trap horse spawned outside and triggered inside yields four untouchable horsemen (by design); a light fading in an already-unloaded chunk at server stop is saved and never cleared.

- [ ] **Step 4: README.md**

Add `set`, `get`, `sweep`, `build` to the commands table (~`:71-77`); fix "A single Mixin" / "One Mixin" (`:171`,`:181`) to two mixins; fix `removal_exempt_types` default (`:105`) to the four actual defaults; fix the install jar version (`:62`) to the current `gradle.properties` version.

- [ ] **Step 5: MODRINTH.md**

Update the commands mention (`:59`,`:64`) to include `set`/`get`/`sweep`/`build`.

- [ ] **Step 6: Commit (after user confirmation)**

```bash
git add AGENTS.md README.md MODRINTH.md
git commit -m "bring docs in line with the implementation"
```

---

### Task 8: Full build and dev-server smoke test

**Files:** none (verification only)

- [ ] **Step 1: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; jar in `build/libs/`.

- [ ] **Step 2: Boot the dev server**

Run: `./gradlew runServer` (background; `run/eula.txt` should already exist — check first, accept if not)
Expected: server reaches "Done"; no mixin apply errors, no config errors in `run/logs/latest.log`.

- [ ] **Step 3: In-game checks (interactive, or via a temporary op'd client)**

From the spec's verification section: activate a conduit (`/mobconduit build <pos>`); `/mobconduit set frame_block minecraft:ancient_debris` → obsidian restored under the crystal, no phantom light; rebuild with the new frame block → activation sound and sweep run; `/mobconduit set frame_block minecraft:air` → rejected with fallback in the log; endermen spawn naturally inside an End conduit's radius; teleport an active crystal → the old zone deactivates within ~2 s. Any check that cannot be driven headlessly is reported as unverified, not claimed.

---

## Self-review notes

- Spec coverage: bugs #1 (T2S1), #2 (T5S1), #3 (T4S1), #4 (T4S2), #5 (T2S2), #6a (T3), #6b (T2S3), #7 (T1); nits: lightCount (T4S3), fade clamp (T5S2), trap counter (T1S2), aura default (T5S3); dead code/javadocs (T6); doc drift (T7); verification (T8). Deferred items stay deferred per the spec.
- Type consistency: `Hostiles.isSuppressible(Entity) -> boolean` used identically in T1S2 and T1S3; `activate` void change has exactly one caller, updated in T2S4.
- `NeutralMob` import path `net.minecraft.world.entity.NeutralMob` verified: `.minecraft-src/net/minecraft/world/entity/NeutralMob.java` exists.
