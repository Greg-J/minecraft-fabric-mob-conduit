# Full Code-Review Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the defects from the 2026-08-03 full-tree code review that the multi-loader batch
did not cover — three core lifecycle bugs, the Bukkit crystal-tracking gap, the paper-plugin
simplification Greg approved, plus a checked-in verification battery and housekeeping.

**Architecture:** Surgical edits to `ConduitStore`, `ModConfig` and the Bukkit listener/command
layer, one deletion (`paper-plugin.yml`) with its fan-out, one new stdlib-only tool. No new
dependencies, no new mixins, no new config keys, no new registry entries.

**Tech Stack:** Java 25, Minecraft 26.2 (Mojang mappings), Fabric Loader 0.19.3, Fabric API
0.156.0+26.2, NeoForge 26.2.0.40-beta, spigot-api (Bukkit main) / paper-api (Bukkit paper source
set), stdlib Python 3.

## Baseline

Written against **`171ff45`** ("resolve curseforge plugin compat via the bukkit-typed version
entry"). `./gradlew build :bukkit:build :neoforge:build mergeJar` is green at that commit —
confirmed, exit 0.

Six commits landed between the review and this plan (`b1611eb`..`171ff45`). **Already fixed —
do not redo:**

- Multi-loader publish plumbing. `publish.yml` builds `mergeJar` and prefers the `-all` jar;
  `publish-modrinth.py` publishes per-loader artifacts; CurseForge loader taxonomy resolved.
  The review's publish finding is closed, and closed better than it was written up.
- `mergeJar` hardening — `afterEvaluate` + task-provider resolution + a descriptor assertion.
- `RadiusVisualizer.clearWorld` on world unload (Bukkit).
- The Bukkit spigot-api/paper-api source-set split. `PaperAccess` (main) now holds a reflective
  `Hooks` holder; `paper/PaperHooks` implements it. **Task 4 below edits both.**

**Still open at `171ff45`, verified by grep:** `ConduitStore.forget` clears at `:525`; the
`revalidate` drop is at `:451-458`; `ModConfig.validate()` has no null coalescing;
`MobConduitListener` still hooks `EntityPlaceEvent` at `:146`; `onWorldUnload` still lacks
`ignoreCancelled` at `:162`; `RadiusVisualizer.arm` has no dedup; `neoforge.mods.toml` has no
`logoFile`; `build.gradle:28-32` still has the datagen block; `lastFeedback` is never pruned.

**Collision warning:** `docs/superpowers/plans/2026-08-03-code-review-fixes.md` is a *different*
plan (the 2026-08-02 adversarial review's deferred findings), mostly already executed. Its Task 4
asserts `paper-plugin.yml` in the uber-jar; **Task 4 of this plan deletes that file**, so that
assertion must lose the entry. Do not treat the two plans as one.

## Decisions already made — do not re-open

Greg was walked through every judgement call on 2026-08-03. These are settled:

| Question | Decision |
|---|---|
| Crystal aura's 512-block particle reach | **Leave it.** The long-reach flag is deliberate; the sustained packet cost is accepted as the price of the only continuous on-state marker. Finding dropped. |
| `bukkit/…/paper-plugin.yml` | **Delete it.** Paper loads via `plugin.yml` like Spigot. See Task 4. |
| Test coverage | **Scripted RCON battery**, stdlib only. Not JUnit, not gametests. See Task 7. |
| Untracked `AGENTS.md` | **Commit the symlink** (it is already a symlink to `CLAUDE.md`, not a copy). |
| Untracked root `icon.jpg` | **Move to `docs/branding/icon-1024.jpg` and commit.** |

## Global Constraints

- **Minecraft names only from `./.minecraft-src/`**, never memory. Yarn names are wrong and will
  not compile. Cite the file path in a comment where the surrounding code already does. If you
  cannot verify a name, say so and stop — do not guess and do not hedge with "it may be called".
- **Server-side only.** No new registry entries. `src/client/` stays empty.
- **Multi-loader parity.** Core changes flow to Fabric *and* NeoForge; Bukkit needs its own
  matching edit **in the same commit**. Each task states whether Bukkit changes — Task 1
  deliberately does not, and "fixing" it symmetrically will break it.
- **Spigot-compile guard stays true.** Bukkit `main` compiles against spigot-api. Any Paper-only
  member goes in `src/paper/java` behind `PaperAccess`. The build is the guard; do not weaken it.
- **Performance.** No new per-tick work, no new radius scans, no new world scans for conduits.
- **Read before you write.** For anything touching a Minecraft, Fabric or Bukkit API, report what
  you found and where before editing.
- **Do not refactor code you were not asked to touch.**
- `./gradlew build :bukkit:build :neoforge:build mergeJar` stays green after every task.
- No version bump, no tagging, no publishing. Commit messages: one line, imperative, lowercase.

## Do not touch — settled design

- The end crystal explosion and vanilla end crystal damage. No invulnerability, no suppression,
  no config option. Do not raise it.
- `minecraft:conduit` as the centre block. Rejected; do not propose reverting.
- The three-stage erasure visuals, the 15-step fade, the riser count. Deliberate one-off costs.
- `forcefield` defaulting to `true` and its 40-tick sweep. Measured; accepted.
- The crystal aura's reach and cadence. Decided above.

---

### Task 1: Stop `forget()` wiping the persisted conduit list

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/ConduitStore.java:510-529`

**Bukkit: no change.** See Step 3.

Verified chain — the teardown runs before the shutdown save, and the save writes what the object
holds at that moment:

| Link | Source |
|---|---|
| Fabric fires `SERVER_STOPPING` at `@At("HEAD")` of `stopServer` | `fabric-lifecycle-events-v1` `MinecraftServerMixin:67` |
| `stopServer` then calls `saveAllChunks(false, true, false)` | `MinecraftServer.java:644`, call at `:678` |
| reaching `ServerLevel.saveLevelData` → `SavedDataStorage.saveAndJoin` | `ServerLevel.java:894` |
| which encodes every cached entry whose `isDirty()` is true | `SavedDataStorage.java:185` |

`forget()` clears `this.conduits` and never resets the dirty flag, so a store dirtied this session
— any `activate` of a new conduit, any `deactivate`, any `revalidate` — is written out as
`{"conduits":[]}`. On a brand-new world it is guaranteed: `SavedDataStorage.set` marks a freshly
created store dirty. NeoForge's `ServerStoppingEvent` sits in the same place.

Corroboration already in the tree: every `conduits.dat` under `run/` and `neoforge/run/` holds a
zero-length conduit list, each written at the same second as its shutdown log line, minutes after
`/mobconduit build 0 100 0`.

Impact is bounded — detection re-derives from crystal + frame within 40 ticks of chunk load, so it
self-heals — but `CLAUDE.md` states persistence as a requirement, and a save file that is always
silently empty is a trap for anything built on it later.

- [ ] **Step 1:** replace the body of `forget` so it does teardown side effects only, keeping the
      conduit list intact:

```java
	/**
	 * Levels are dropped wholesale on shutdown. Restores every swapped base block, returns every
	 * in-flight light to air, and resets the static counters so nothing leaks into the next world
	 * on an integrated server.
	 *
	 * <p>Deliberately does <em>not</em> clear {@link #conduits}. This runs from SERVER_STOPPING,
	 * which Fabric fires at the head of {@code MinecraftServer.stopServer}
	 * ({@code MinecraftServer.java:644}) — before the shutdown save at {@code :678}. That save
	 * encodes every cached SavedData whose dirty flag is set ({@code SavedDataStorage.java:185}),
	 * so emptying the list here wrote {@code conduits: []} over the real state on every session
	 * that had touched a conduit since the last autosave.
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
		this.pendingDeactivations.clear();
		// The index goes, so nothing consults stale coverage after teardown; the list itself
		// stays, so the shutdown save persists it.
		this.byChunk.clear();
		this.effects.clearAll(level);
		syncEffectsFlag();
	}
```

- [ ] **Step 2:** confirm the two consequences of retaining the list match this description, and
      add no guard beyond them:
  - `isActiveAt(pos)` stays true through the shutdown chunk-unload loop, so `onEntityUnload` parks
    deactivations into `pendingDeactivations`. Nothing drains them; no `setDirty`, no block writes.
  - `countedInGlobal` is now 0 while `conduits` is non-empty. Nothing calls `reindex()` on this
    instance again — `stopServer` runs `chunkSource.tick`, never `level.tick`, so END_LEVEL_TICK
    does not fire after SERVER_STOPPING. **Verify this in `MinecraftServer.java:644-695` before
    accepting it.** If you find a real caller, report it rather than papering over it.

- [ ] **Step 3:** **do not change `bukkit/…/ConduitStore.forget`.** It already calls `save(world)`
      *before* clearing, with a comment explaining this exact hazard. Clearing is correct there
      because Bukkit persistence is an explicit file write, not a dirty-flag sweep. The asymmetry
      is the right answer.

- [ ] **Step 4:** `./gradlew build` green.

---

### Task 2: Stop `revalidate()` stranding a light block and a hologram

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/ConduitStore.java:451-458`
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/ConduitStore.java:587-594`

The `!isLoaded && dimDisabled` branch drops the conduit with only `armedThisSession.remove(pos)`
— no `restoreBase`, no `Holograms.remove`. The other three drop paths in the same method do both.
Once dropped, the crystal's next validation calls `store.deactivate`, which returns early because
`find(pos)` is null. Result: a permanent invisible `minecraft:light` where the player's obsidian
was, plus an orphan "Mob Conduit / radius N" text display.

**Do not fix this by calling `restoreBase` / `Holograms.remove` in that branch.** Both are no-ops
on an unloaded chunk by design — `restoreBase` early-returns on `!level.isLoaded`, and
`Holograms.findAll` is a section query that finds nothing when unloaded. Calling them changes
nothing.

**Do not force-load the chunk either.** The fix is to stop dropping, because the comment
justifying the drop is already false. A retained entry in a disabled dimension suppresses nothing:
both spawn guards short-circuit on the dimension *before* consulting the store —
`MobConduit.java:60` and `bukkit/…/MobConduitListener.java:39`. And it *is* removable: when the
chunk loads, the crystal ticks, `ConduitDetector.validate` sees `isDimensionDisabled` and calls
`store.deactivate`, which now finds the entry and runs the full teardown.

- [ ] **Step 1:** in the core `revalidate`, collapse the `!level.isLoaded(pos)` branch to the
      survivor path only:

```java
			if (!level.isLoaded(pos)) {
				// Cannot read the frame; keep it and let the crystal's next tick decide. Kept even
				// when the dimension is disabled: the guard already short-circuits on the dimension
				// before consulting the store (MobConduit.allowSpawn), so a retained entry
				// suppresses nothing — and keeping it is what lets the crystal's next validation
				// run the real teardown, restoreBase and Holograms.remove, instead of stranding
				// both forever on a position nothing can reach.
				survivors.add(new Conduit(pos, conduit.frameCount()));
				continue;
			}
```

- [ ] **Step 2:** make the identical change in the Bukkit sibling, guarding on
      `!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)` and citing
      `MobConduitListener.onCreatureSpawn` instead.

- [ ] **Step 3:** leave the three loaded-chunk drop paths in both files exactly as they are — they
      already tear down correctly. Note in the commit message that `dropped` now counts fewer
      conduits on this path, so the command reply is correspondingly more accurate.

- [ ] **Step 4:** `./gradlew build :bukkit:build` green.

---

### Task 3: Config keys must survive an explicit JSON `null`

**Files:**
- Modify: `src/main/java/io/github/gregj/mobconduit/ModConfig.java:336` (`validate()`)
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/ModConfig.java:341` (`validate()`)

`keys()`, `describe()` and `set()` all walk `GSON.toJsonTree(active)`, and Gson omits null fields.
`validate()` tolerates nulls (`if (names != null)`, `resolveEnum` catching `NullPointerException`)
but never restores the field. So `"suppression_feedback": null` in the file makes
`/mobconduit set suppression_feedback actionbar` fail with *"Unknown setting"*, and the key
vanishes from the file on the next save. `frame_block` survives only by accident, because
`validate()` rewrites it whenever the resolved block equals the default.

- [ ] **Step 1:** add a null-coalescing block at the very top of the core `validate()`, before
      `resolveBlock` runs, restoring the same literals the field initialisers use:

```java
		// Gson writes an explicit JSON null straight onto the field, and toJsonTree then omits the
		// key entirely — which makes it unreachable from keys(), get() and set(). Restore the
		// declared defaults first so every key always round-trips.
		if (this.frameBlock == null) {
			this.frameBlock = "minecraft:netherite_block";
		}

		if (this.suppressionFeedback == null) {
			this.suppressionFeedback = "actionbar";
		}

		if (this.radiusShape == null) {
			this.radiusShape = "sphere";
		}

		if (this.removalExemptTypes == null) {
			this.removalExemptTypes = List.of(
					"minecraft:wither", "minecraft:ender_dragon", "minecraft:warden", "minecraft:elder_guardian");
		}

		if (this.suppressExemptTypes == null) {
			this.suppressExemptTypes = List.of();
		}

		if (this.disabledDimensions == null) {
			this.disabledDimensions = List.of();
		}
```

- [ ] **Step 2:** do the same for all seven particle-id `String` fields — `crystalAuraParticle`,
      `killPlumeParticle`, `killBeamParticle`, `frameDripParticle`, `removalParticle`,
      `removalSecondaryParticle`, `removalRiserParticle`. A null there currently resolves to the
      fallback particle but leaves the key missing from the file.

- [ ] **Step 3:** mirror both steps in the Bukkit `ModConfig.validate()`. Field names and default
      literals are identical; only the resolved types differ.

- [ ] **Step 4:** leave the `if (names != null)` guards inside `resolveTypeSet` and
      `resolveDimensions`. They are now dead but harmless defence in depth; removing them is out
      of scope.

- [ ] **Step 5:** `./gradlew build :bukkit:build` green.

---

### Task 4: Delete `paper-plugin.yml` and the branch it forced

**Files:**
- Delete: `bukkit/src/main/resources/paper-plugin.yml`
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/MobConduitCommand.java` (`register`, `Dispatch`, imports, class javadoc)
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/PaperAccess.java` (`Hooks.registerCommand`, `registerCommand`)
- Modify: `bukkit/src/paper/java/io/github/gregj/mobconduit/bukkit/paper/PaperHooks.java` (drop the override + `Bukkit`/`Command` imports)
- Modify: `bukkit/build.gradle:46` (`processResources` `filesMatching`)
- Modify: `build.gradle` (uber-jar descriptor assertion + the comment above `mergeJar`)
- Modify: `docs/superpowers/specs/2026-08-02-multi-loader-design.md:70,79-80`

`paper-plugin.yml` declares strictly less than `plugin.yml` — no `commands:`, no `permissions:` —
but its presence is what makes Paper take the paper-plugin path, which never reads `plugin.yml`'s
command section. That forces the whole branch in `register()`: a `PaperAccess.available()` check,
`PaperAccess.registerCommand`, a private `Dispatch extends Command` inner class, and a
programmatic `addPermission` — all of it existing to undo what `paper-plugin.yml` took away. The
plugin declares no bootstrapper, no loader, no dependencies and no libraries, so it uses nothing
that paper-plugins provide.

`PaperAccess` and `PaperHooks` both stay — `spawnReason` still needs them, and so does
`ActionBars.detect()`. Only the command hook goes.

- [ ] **Step 1:** delete `bukkit/src/main/resources/paper-plugin.yml`.

- [ ] **Step 2:** in `MobConduitCommand.register()`, drop the `PaperAccess.available()` branch and
      the programmatic `addPermission` (plugin.yml already declares `mobconduit.admin` with
      `default: op`), leaving only the `plugin.yml` path:

```java
	void register() {
		// plugin.yml declares both the command and the mobconduit.admin permission node, on Paper
		// and Spigot alike — the jar ships no paper-plugin.yml, so Paper takes the same path.
		PluginCommand command = this.plugin.getCommand("mobconduit");

		if (command != null) {
			command.setExecutor(this);
			command.setTabCompleter(this);
		}
	}
```

- [ ] **Step 3:** delete the `Dispatch` inner class. Update the class javadoc — the "two
      registration paths, one handler" paragraph is no longer true. Remove the now-unused
      `Permission` and `PermissionDefault` imports; keep `Bukkit` (used by `senderWorld`) and
      `Command` (used by the `onCommand`/`onTabComplete` signatures).

- [ ] **Step 4:** remove `registerCommand` from `PaperAccess.Hooks` and from `PaperAccess`, and
      remove the override plus the `Bukkit`/`Command` imports from `PaperHooks`. Update both class
      javadocs, which currently name `Bukkit#getCommandMap()` as a reason the paper source set
      exists — `Entity#getEntitySpawnReason()` and the Adventure sender are now the only reasons.

- [ ] **Step 5:** `bukkit/build.gradle:46` — `filesMatching(['plugin.yml', 'paper-plugin.yml'])`
      becomes `filesMatching('plugin.yml')`.

- [ ] **Step 6:** in the root `build.gradle`, drop `'paper-plugin.yml'` from the `mergeJar`
      `doLast` descriptor list, leaving three. Update the comment above `mergeJar` that says
      "the Bukkit plugin (plugin.yml + paper-plugin.yml)".

- [ ] **Step 7:** update `docs/superpowers/specs/2026-08-02-multi-loader-design.md` — line 70's
      "Both `plugin.yml` (Spigot) and `paper-plugin.yml` (Paper preferred)" and the "all four
      descriptors" list at 79-80. Record *why* it was dropped, so this is not re-added later.

- [ ] **Step 8:** `./gradlew :bukkit:build mergeJar` green, and
      `unzip -l build/libs/mob-conduit-*-all.jar | grep -c paper-plugin.yml` returns 0.

- [ ] **Step 9:** flag in the commit message that this needs a Paper boot test — the plugin loads,
      `/mobconduit status` runs, and a non-op is refused — which is Greg's to run. Do not claim it
      verified without that.

---

### Task 5: Bukkit crystal tracking must not depend on `EntityPlaceEvent`

**Files:**
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/MobConduitListener.java:144-150` and its imports

**Core: no change** — Fabric and NeoForge hook the crystal's own tick via `EndCrystalMixin`.

`ConduitDetector.track` is reached only from `EntityPlaceEvent`, `EntitiesLoadEvent`, the
`onEnable` scan, and `/mobconduit build`. If `EntityPlaceEvent` does not fire when a player places
an end crystal on obsidian, a freshly built conduit stays invisible until its chunk reloads or the
server restarts — and the RCON battery structurally cannot catch it, because `/mobconduit build`
calls `track()` explicitly. Rather than block on verifying CraftBukkit's behaviour, widen the net.

`org.bukkit.event.entity.EntitySpawnEvent` is confirmed present in paper-api 26.2.build.91 (and is
Bukkit core, not Paper-only, so it compiles against spigot-api too). It is cancellable, fires for
every entity added to a world including `world.spawn` and item-placed crystals, and does **not**
fire for chunk-loaded entities — which is why `onEntitiesLoad` must stay.

- [ ] **Step 1:** replace the `EntityPlaceEvent` handler with:

```java
	/**
	 * Any crystal entering the world enters the tracked set; the poll validates it within 2s.
	 *
	 * <p>{@code EntitySpawnEvent} rather than {@code EntityPlaceEvent}: place fires only for the
	 * item-use path, so a crystal arriving any other way — another plugin, a command, a structure
	 * — would never be tracked, and Fabric's crystal-tick mixin has no such gap. This fires for
	 * every entity add, so the body stays one instanceof and {@code track} is a putIfAbsent.
	 * Chunk-loaded crystals do not come through here; {@link #onEntitiesLoad} covers those.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntitySpawn(EntitySpawnEvent event) {
		if (event.getEntity() instanceof EnderCrystal crystal) {
			ConduitDetector.track(crystal);
		}
	}
```

- [ ] **Step 2:** swap the import — drop `org.bukkit.event.entity.EntityPlaceEvent`, add
      `org.bukkit.event.entity.EntitySpawnEvent`. Confirm it resolves against **spigot-api**, not
      just paper-api, or the compile guard fails.

- [ ] **Step 3:** leave `onEntitiesLoad`, the `onEnable` scan and `/mobconduit build`'s explicit
      `track()` in place.

- [ ] **Step 4:** `./gradlew :bukkit:build` green.

---

### Task 6: `WorldUnloadEvent` must honour cancellation

**Files:**
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/MobConduitListener.java:162`

`WorldUnloadEvent` is cancellable. At `MONITOR` without `ignoreCancelled = true`, a cancelled
unload still tears the store down, restores base blocks and clears in-flight effects on a world
that keeps running.

- [ ] **Step 1:** add `ignoreCancelled = true` to the `@EventHandler` on `onWorldUnload`. Nothing
      else changes; the `RadiusVisualizer.clearWorld` call added on 2026-08-02 stays.

- [ ] **Step 2:** while in the file, confirm `EntityRemoveEvent` is not cancellable, so
      `onEntityRemove` correctly has no `ignoreCancelled`. Check the Bukkit API, do not assume.

- [ ] **Step 3:** `./gradlew :bukkit:build` green.

---

### Task 7: Checked-in RCON verification battery

**Files:**
- Create: `tools/rcon-battery.py`
- Modify: `CLAUDE.md` (the "In-game verification" paragraph under Commands)

Greg chose a scripted battery over JUnit or gametests, and the reasoning is worth keeping in the
file header: of the three real defects in this plan, only Task 3 is unit-testable — Task 1 is
only visible if something stops and restarts a server and reads state back, which is exactly what
this covers.

**Scope discipline.** This asserts *state*: conduit registration, persistence across restart,
block swaps, hologram presence, config round-trips. It must **not** assert on spawn-suppression
rates or anything player-driven — natural spawning barely runs with no player online, and
headless conclusions about it have been wrong before. Say so in the header.

- [ ] **Step 1:** write `tools/rcon-battery.py`, stdlib only, importing `rcon` from
      `tools/rcon.py` rather than reimplementing the protocol. Two phases, selected by argv, so
      the script never has to launch or wait on Gradle:
  - `pre-stop` — assert the server is reachable; `mobconduit build 0 100 0`; sleep past the 40-tick
    validation; assert `mobconduit status` reports one active conduit; assert the hologram exists
    (`execute if entity @e[type=text_display,x=0,y=104,z=0,distance=..3]`); assert the base swapped
    (`execute if block 0 100 0 minecraft:light`); exercise `set`/`get`/`reload`/`sweep`/`visualize`
    and assert none replies with an error; then issue `stop`.
  - `post-start` — assert `mobconduit status` reports the conduit **before any crystal has ticked**
    (this is the Task 1 regression); assert the base is obsidian or light consistently with
    `light_base_on_activate`; assert exactly one hologram, not two (the dedup path).
- [ ] **Step 2:** exit non-zero with a readable diff on any failed assertion, and print each check
      as it passes, so a human reading the output can see coverage.
- [ ] **Step 3:** default to port 25575 / password `mobconduit`, overridable by argv, so the same
      script drives the NeoForge dev server and a Paper test server.
- [ ] **Step 4:** update `CLAUDE.md`'s "In-game verification" paragraph to point at the battery
      and to state plainly that it covers state, not gameplay, and is not a substitute for Greg's
      own testing.
- [ ] **Step 5:** run it against the Fabric dev server, both phases, with the Task 1 fix in place.
      Before starting any server, check the ports are free
      (`lsof -nP -iTCP:25565 -sTCP:LISTEN`) — Greg runs other Minecraft projects on the same
      ports. If something is listening, ask before bouncing anything.
- [ ] **Step 6:** run it against the NeoForge dev server (`./gradlew :neoforge:runServer`).

---

### Task 8: Commit the two untracked files

**Files:**
- Add: `AGENTS.md` (existing symlink → `CLAUDE.md`)
- Move + add: `icon.jpg` → `docs/branding/icon-1024.jpg`

- [ ] **Step 1:** `git add AGENTS.md`. Confirm git records it as mode `120000`
      (`git ls-files -s AGENTS.md`), not as a 24 KB blob — if it is a blob, the symlink was
      dereferenced and it will drift from `CLAUDE.md`.
- [ ] **Step 2:** `mkdir -p docs/branding && git mv`-equivalent for the untracked file: move
      `icon.jpg` to `docs/branding/icon-1024.jpg`, then `git add` it.
- [ ] **Step 3:** add a one-line `docs/branding/README.md` recording that the 1024×1024 jpg is the
      master for the 256×256 `src/main/resources/assets/mob-conduit/icon.png` and for the
      Modrinth/CurseForge project art.

---

### Task 9: Housekeeping batch

Independent items; do them all or state which you skipped and why. One commit.

- [ ] **9.1 — `lastFeedback` never shrinks.** `ConduitStore.java:88` documents the map as
      "transient, self-cleaning by size"; nothing ever removes an entry, not even `deactivate`.
      Remove the entry in `deactivate` (both modules) so the claim becomes true. One line each.
- [ ] **9.2 — `RadiusVisualizer.arm` has no dedup.** Repeated `/mobconduit visualize` stacks
      visuals on the same conduit, each emitting its own band. Reset an existing visual's
      `ticksLeft` instead of adding a second. Both modules.
- [ ] **9.3 — `RemovalEffects.enqueue` has no dedup.** Overlapping conduits, or
      `forcefield_interval_ticks` below `removal_light_delay_ticks`, enqueue the same mob
      repeatedly and inflate `pendingRemovalCount()`. Skip a mob already in `queued` or `armed`.
      Keep it cheap — this runs per swept mob. Both modules.
- [ ] **9.4 — Dead datagen config.** `build.gradle:28-32` still carries
      `fabricApi { configureDataGeneration { client = true } }` for a mod whose hard rule is that
      `src/client` stays empty and the client never installs anything. Remove the block, then
      **verify `./gradlew build` still succeeds** — if a task depends on the generated source set,
      stop and report rather than chasing it.
- [ ] **9.5 — NeoForge ships without an icon.** `neoforge.mods.toml` has no `logoFile`, though the
      NeoForge jar already contains `assets/mob-conduit/icon.png` (confirmed by `unzip -l`). Add
      `logoFile="assets/mob-conduit/icon.png"` to the `[[mods]]` block.
- [ ] **9.6 — Misleading javadoc.** `MobConduitListener.onCreatureSpawn` says raids, patrols,
      sieges, reinforcements and portal piglins are "the leaks the Fabric platform cannot close,
      this one can". Bukkit's richer reason set classifies them **non-natural**, so they are *not*
      suppressed — the same outcome as Fabric. Rewrite to say what the code does: Bukkit names
      these paths explicitly instead of lumping them under a vague reason, which makes the
      classification more precise, not the suppression broader.
- [ ] **9.7 — Undocumented behaviour.** Add to `CLAUDE.md`'s "Known behaviors and unavoidable side
      effects": a conduit deactivates when its own chunk unloads, but its radius can still cover
      loaded, ticking chunks — a player ~200 blocks out leaves roughly a 72–160 block band
      unsuppressed until they return. Self-healing, because re-activation re-arms and re-sweeps.
- [ ] **9.8 — Empty saved data in every dimension.** `MobConduit.onLevelTick` calls
      `ConduitStore.get(level)` for every level whenever any level is busy; the first such call in
      a conduit-free dimension creates a `SavedData`, and `SavedDataStorage.set` marks it dirty, so
      an empty `conduits.dat` is written for every dimension. Cosmetic. Fix only if it costs
      nothing. **Do not** restructure the tick hook to chase it.

---

## Final verification

- [ ] `./gradlew clean build :bukkit:build :neoforge:build mergeJar` — exit 0, no new warnings.
- [ ] Uber-jar carries exactly three plugin descriptors — `fabric.mod.json`,
      `META-INF/neoforge.mods.toml`, `plugin.yml` — plus both mixin configs and the access
      transformer. No `paper-plugin.yml`.
- [ ] Fabric dev server boots clean; `grep -iE "error|exception" run/logs/latest.log` shows nothing
      new.
- [ ] NeoForge dev server boots clean; same grep against `neoforge/run/logs/latest.log`.
- [ ] `tools/rcon-battery.py` passes both phases on Fabric and on NeoForge.
- [ ] Task 1's regression specifically: the conduit is present in `conduits.dat` after a clean
      `stop` —
      `python3 -c "import gzip;print(gzip.open('run/world/dimensions/minecraft/overworld/data/mob-conduit/conduits.dat','rb').read())"`
      must show the position, not a zero-length list — and `mobconduit status` reports it
      immediately on the next boot.

**Then stop.** Paper and Spigot boot tests (Task 4), and any judgement about whether suppression,
sweeps and the visuals feel right, are Greg's. Say the build is ready and hand back. Do not run
gameplay tests headlessly and report a verdict.

## Commits

One per task, in order. **Pause and ask before the first `git commit`.** Do not push, do not tag.

```
stop the shutdown teardown wiping persisted conduits
keep unloaded conduits so revalidate cannot strand a light and hologram
restore null config keys so every setting stays reachable
drop paper-plugin.yml and load through plugin.yml on both platforms
track bukkit crystals from entity spawn rather than entity place
honour a cancelled world unload
add a checked-in rcon verification battery
commit the agents symlink and the master icon
housekeeping from the 2026-08-03 full review
```
