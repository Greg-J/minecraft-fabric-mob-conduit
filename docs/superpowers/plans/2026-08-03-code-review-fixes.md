# Code-Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close out the deferred findings from the 2026-08-02 adversarial multi-loader review, add the Spigot-compile guard, and make the publish pipeline multi-loader aware so 1.3.0 is releasable.

**Architecture:** Seven small, independent fixes across the Bukkit module, the NeoForge module docs, the root build, and the publish tooling. No behavior changes on Fabric.

**Tech Stack:** Java 25, Gradle, Bukkit/Paper API, Modrinth/CurseForge upload scripts (stdlib Python).

## Global Constraints

- The Spigot-compatible claim must stay true: no unguarded Paper-only API in `bukkit/` main sources.
- `./gradlew clean build mergeJar` must stay green after every task.
- No version bump, no tagging, no publishing — release is the user's call.
- Commit messages: one line, imperative, lowercase.

---

### Task 1: Bukkit feedback parity — red error replies (M-11)

**Files:**
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/MobConduitCommand.java`

Fabric's `sendFailure` renders red; Bukkit currently sends plain white. Add a small
`fail(CommandSender, String)` helper that prefixes `org.bukkit.ChatColor.RED` and route every
error/usage reply through it (`setConfig` unknown-key path `:213`, `getConfig` `:231`, usage
lines `:202,:224,:244,:259`). Success replies stay default-colored.

- [ ] **Step 1:** add `private static void fail(CommandSender sender, String message) { sender.sendMessage(org.bukkit.ChatColor.RED + message); }` (import ChatColor).
- [ ] **Step 2:** replace the error/usage `sender.sendMessage(...)` calls listed above with `fail(...)`. Do not touch success replies.
- [ ] **Step 3:** `./gradlew :bukkit:build` green.

### Task 2: `build` command caret coordinates (M-10)

**Files:**
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/MobConduitCommand.java:242-294`

The parser supports `~` but not `^` (vanilla local coordinates, relative to look direction).
Extend `build` to accept `^`-prefixed coords: compute the source's local axes from yaw/pitch
(`org.bukkit.util.Vector` from `Location#getDirection`, plus left = direction × up) and resolve
`^a ^b ^c` as `pos + left*a + up*b + forward*c`. Source rotation: player look; command blocks
and console default to facing south (yaw 0, pitch 0), matching vanilla's fallback. Mixed
coordinates (`~5 ^ 12`) are out of scope — if any arg starts with `^`, all three are parsed as
caret; vanilla has the same restriction.

- [ ] **Step 1:** in `build`, branch: all-caret args → `parseCaretCoordinates(args, origin, direction)`; else existing per-arg `parseCoordinate`.
- [ ] **Step 2:** write `parseCaretCoordinates` (+ a `sourceDirection(sender)` helper: `Player#getLocation().getDirection()`, else south). Reject non-numeric caret components with a clear `fail(...)`.
- [ ] **Step 3:** build green; on the Paper test server, `/mobconduit build ^ ^5 ^` from a player-less console is untestable for direction, so verify numeric parsing via `build ~ ~5 ~` regression and `build ^ ^ ^` lands at the source position.

### Task 3: Visualizer cleanup on world unload (M-4)

**Files:**
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/RadiusVisualizer.java:70`
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/MobConduitListener.java:163-165`

A multiverse-style unload mid-visual parks the visuals forever. Add
`RadiusVisualizer.clearWorld(World)` (remove `Visual` entries whose world matches) and call it
from `onWorldUnload` alongside the existing `ConduitStore.onWorldUnload`.

- [ ] **Step 1:** add `clearWorld` (iterate `ACTIVE`, `removeIf` on world identity).
- [ ] **Step 2:** call it in `MobConduitListener.onWorldUnload`.
- [ ] **Step 3:** build green.

### Task 4: `mergeJar` robustness (M-7)

**Files:**
- Modify: `build.gradle:75-101`

Two hardenings: (a) stop hardcoding the Fabric jar path — resolve it from the `remapJar` task
provider inside an `afterEvaluate` (Loom registers the task lazily, which is why the path was
hardcoded); (b) after merging, fail the build if any of the four descriptors
(`fabric.mod.json`, `META-INF/neoforge.mods.toml`, `plugin.yml`, `paper-plugin.yml`) is missing
from the output — guards against silent input divergence.

- [ ] **Step 1:** wrap the `mergeJar` registration in `afterEvaluate { ... }` and use `tasks.named('remapJar')` (it exists by then) with `dependsOn` + `zipTree` on the provider, dropping the `libs/mob-conduit-${project.version}.jar` literal and the `build` dependency in favor of `remapJar`.
- [ ] **Step 2:** `doLast { def zf = new java.util.zip.ZipFile(archiveFile.get().asFile); ['fabric.mod.json','META-INF/neoforge.mods.toml','plugin.yml','paper-plugin.yml'].each { name -> if (zf.getEntry(name) == null) throw new GradleException("uber-jar is missing $name") } }`.
- [ ] **Step 3:** `./gradlew clean mergeJar` green; descriptor check provably runs (temporarily renaming one input is optional — code inspection suffices).

### Task 5: Bukkit source-set split — Spigot-compile guard (I-3 follow-up)

**Files:**
- Modify: `bukkit/build.gradle`
- Move: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/PaperAccess.java` → `bukkit/src/paper/java/...`
- Modify: `bukkit/src/main/java/io/github/gregj/mobconduit/bukkit/ActionBars.java`
- Create: `bukkit/src/paper/java/io/github/gregj/mobconduit/bukkit/paper/PaperActionBarSender.java`, `paper/PaperSpawnReasons.java`

Today nothing stops an unguarded Paper-only call from compiling into the main sources. Split:
`main` compiles against **spigot-api** (`org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT`, repo
`https://hub.spigotmc.org/nexus/content/repositories/snapshots/`); a new `paper` source set
compiles against **paper-api** and holds everything Paper-only. Main code references paper
impls only by name (`Class.forName` + a shared interface defined in main).

- [ ] **Step 1:** move `PaperAccess`'s `spawnReason` body into `paper/PaperSpawnReasons implements SpawnReasonLookup` (interface `SpawnReasonLookup` in main, `CreatureSpawnEvent.SpawnReason spawnReason(Entity)`); main's `PaperAccess` keeps only `available()` + a reflective holder returning the lookup or null. Update `SpawnOrigins` to go through the holder.
- [ ] **Step 2:** same pattern for the action bar: `AdventureSender` moves to `paper/PaperActionBarSender implements ActionBars.Sender` (interface stays nested in main's `ActionBars`); `ActionBars.detect()` instantiates it via `Class.forName(...).getDeclaredConstructor().newInstance()` when Paper+Adventure checks pass.
- [ ] **Step 3:** `bukkit/build.gradle`: `sourceSets { paper { java.srcDir 'src/paper/java' } }`; `compileOnly 'org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT'` for main; `paperCompileOnly 'io.papermc.paper:paper-api:26.2.build.91-stable'`; jar `from sourceSets.paper.output`; verify spigot-api version resolves (check the spigot nexus listing if the snapshot name 404s).
- [ ] **Step 4:** `./gradlew :bukkit:build` green. The main compile succeeding against spigot-api IS the guard — any unguarded Paper-only reference now fails the build.
- [ ] **Step 5:** Paper + Spigot smoke boots still load (descriptors unchanged; jar now includes the `paper/` classes).

### Task 6: NeoForge `getSpawnType` note (M-12, docs-only)

**Files:**
- Modify: `neoforge/src/main/java/io/github/gregj/mobconduit/neoforge/mixin/EntityTypeMixin.java`

NeoForge's patched `Mob.finalizeSpawn` already stores `spawnType`, so `Mob#getSpawnType()`
could replace the WeakHashMap capture for mobs. Keep the current two-layer design (it mirrors
Fabric exactly and covers non-`Mob` paths uniformly), but record the option in the class
javadoc so a future maintainer knows it was considered.

- [ ] **Step 1:** add the javadoc paragraph. No code change.

### Task 7: Multi-loader publish plumbing

**Files:**
- Modify: `tools/publish-modrinth.py`
- Modify: `tools/publish-curseforge.py`
- Modify: `.github/workflows/publish.yml`

Current: one jar, loaders `["fabric"]` / `"Fabric"`, required Fabric API dependency. Target:
**two files per release** — the Fabric jar (loaders `[fabric]`, required Fabric API dep) and
the all jar (loaders `["neoforge","paper","spigot"]`, no deps). Two files in one Modrinth
version with disjoint loader sets is first-class (Chunky does exactly this); CurseForge takes
two uploads with their own loader tags.

- [ ] **Step 1:** `publish-modrinth.py`: replace the single-artifact flow with an `ARTIFACTS` list — `(jar_path, loaders, dependencies)` tuples; create the version with both files; Fabric API stays required only on the fabric file.
- [ ] **Step 2:** `publish-curseforge.py`: same two-upload flow with per-file loader ids (resolve `neoforge`, `paper`, `spigot` names to CurseForge's numeric ids from its API, as it already does for Fabric).
- [ ] **Step 3:** `publish.yml`: build `mergeJar`; run each publish step for both artifacts; the version check keeps reading `fabric.mod.json` (present in both jars).
- [ ] **Step 4:** `DRY_RUN=true` local run of both scripts against the real jars to prove the payloads build without uploading.

### Task 8: Verification battery re-run + commits

- [ ] **Step 1:** `./gradlew clean build mergeJar` green; uber-jar contains all four descriptors (automated by Task 4's check).
- [ ] **Step 2:** Paper battery (port 25576): build/activate, hologram toggle, `build ^ ^ ^` sanity, `build ~ ~5 ~` regression, forcefield zombie sweep, teleport deactivation.
- [ ] **Step 3:** Spigot battery (port 25577): same core checks — proves the source-set split didn't break the legacy paths.
- [ ] **Step 4:** NeoForge quick check (`:neoforge:runServer`): build/activate/sweep.
- [ ] **Step 5:** Commits, one per task group (feedback+coords, unload cleanup, mergeJar, source split, neoforge doc, publish plumbing).

## Self-review notes

- Review coverage: M-11→T1, M-10→T2, M-4→T3, M-7→T4, I-3-guard→T5, M-12→T6, publish→T7.
  Deliberately not fixed: M-1 (permission-model divergence — platform-idiomatic, documented in
  code), M-2 (command-block success counts — Bukkit plugins cannot propagate them, platform
  limitation). M-3/M-5/M-6/M-8/M-9 were fixed on 2026-08-02 already.
- Type consistency: `ActionBars.Sender` stays the shared interface name; `SpawnReasonLookup`
  is new in Task 5 and used by both `PaperAccess` (main, reflective) and `PaperSpawnReasons`
  (paper).
