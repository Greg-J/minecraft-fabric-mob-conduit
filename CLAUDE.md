# CLAUDE.md — Mob Conduit

Multi-loader Minecraft mod: Fabric, NeoForge, Paper and Spigot, from one codebase. Mod ID
`mob-conduit` (Fabric/Bukkit), `mobconduit` (NeoForge — its modids disallow hyphens), maven
group / base package `io.github.gregj.mobconduit`.

**Naming gotcha:** the mod ID contains a hyphen, which is illegal in Java identifiers. Use
`mob-conduit` for the mod ID, resource namespaces, and asset paths. Use `mobconduit` wherever
a Java identifier is required, including the Mixin injected-method prefix (`mobconduit$onX`).

Versions live in `gradle.properties` — treat that file as the source of truth, not this
document:

| | |
|---|---|
| Minecraft | `26.2` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.156.0+26.2` |
| Loom | `1.17-SNAPSHOT` (a moving snapshot, not a pinned release) |
| NeoForge | `26.2.0.40-beta` (ModDevGradle 2.0.141) |
| Paper API | `26.2.build.91-stable` (compile target; runs on Spigot too) |
| Java | 25 |

`build.gradle` calls `splitEnvironmentSourceSets()`, so common code lives in `src/main` and
client-only code in `src/client`. `settings.gradle` pins `rootProject.name = 'mob-conduit'` to
match the mod ID; it is deliberately independent of the folder name.

---

## What this mod does

Mob Conduit adds a player-built multiblock structure that prevents hostile mob spawning in a
radius around it, built entirely from vanilla blocks.

**Structure:**

- **Center:** a vanilla `minecraft:end_crystal` entity, placed on an obsidian block. The
  crystal's own idle animation gives the structure a visible "on" state with no client mod.
  **Frame geometry centers on the crystal's position**, not the obsidian beneath it.
- **Frame:** a configurable vanilla block, defaulting to `minecraft:netherite_block`, arranged
  in the same geometry as the vanilla prismarine conduit frame.
- Frame block count drives the radius, on vanilla's thresholds.

The design goal is a legible, expensive, endgame answer to base spawn-proofing that reuses a
shape players already understand, rather than inventing new building rules.

**The crystal is destructible and the explosion is a feature.** Leave vanilla end crystal
damage and explosion behavior completely untouched. No invulnerability, no damage cancelling,
no explosion suppression, no config option for any of it. This is settled; do not raise it. The
player replaces the crystal to reactivate.

### Resolved design

- **No water requirement.** Unlike the vanilla conduit, this works in air. Vanilla's activation
  check cannot be reused directly.
- **Hostile mobs only.** The rule, as implemented by the shared `Hostiles.isSuppressible`
  predicate (used by both the spawn guard and the erasure sweep): `Enemy` minus `NeutralMob`,
  plus the three undead mounts. In 26.2 the only `Enemy & NeutralMob` types are endermen and
  zombified piglins, so those stay spawnable and unswept; spiders and piglins are `Enemy`
  without `NeutralMob` and stay suppressed. Passive spawns are unaffected.
- **Natural spawns only.** Monster spawners, spawn eggs, and breeding all keep working.
- **Radius scales with frame block count**, from `radius_min` to `radius_max` (default 64 to
  128 blocks), across the same frame thresholds vanilla uses.
- **On activation, existing hostile mobs in radius are removed**, with a particle effect per
  mob.

### Removal-on-activation rules

- **Remove, do not kill.** No drops, no XP, no death event. This is a performance decision:
  killing several hundred hostiles at once drops hundreds of item stacks and XP orbs in a
  single tick, and they persist for five minutes. It also fits the effect better — the mobs are
  erased, not slaughtered.
- `removal_drops` opts out of that, routing through `LivingEntity#kill` (`LivingEntity.java:320`)
  so loot tables, XP and advancements all fire. It defaults off. Note that it compounds with
  `forcefield`: together they make a perpetual grinder rather than a one-off payout, which is a
  materially different thing from what the paragraph above is weighing.
- **Exempt:** boss entities (a player must not be able to delete a Wither by activating a
  conduit), named mobs, anything flagged persistent, tamed mobs, leashed mobs, raid members,
  and the output of player-built machinery (spawners, trial spawners, breeding, conversions).
  Spawn-egg and `/summon` hostiles are deliberately **not** exempt: they spawn fine, since
  suppression only vetoes natural spawns, and the forcefield then treats them like any other
  hostile standing in the radius.
- **Wanderers are covered by `forcefield`, which defaults on.** The one-time activation sweep
  handles what is already inside. Everything else is handled by spawn suppression — except mobs
  that spawn *outside* the radius and walk in. Measured on a live server: 5142 of 5310 natural
  hostile spawn attempts were suppressed, and the remaining 168 spawned out of range. Those 168
  are what a player sees and reads as "it isn't working", which is why `forcefield` is on by
  default. Turning it off restores pure suppression, at which point walk-ins persist.
- `forcefield` re-sweeps each conduit every `forcefield_interval_ticks` (default 40). That is
  the radius scan the rest of the mod is built to avoid, so it hangs off the end crystal's
  existing throttle rather than a per-tick check. Suppression itself still costs nothing per
  tick.
- **Stage the removal across ticks with a budget.** A 128-block radius can hold hundreds of
  entities; removing them plus spawning particles in a single tick is a visible stutter.
- **Particle per removed mob:** `minecraft:soul_fire_flame` (`ParticleTypes.SOUL_FIRE_FLAME`,
  `core/particles/ParticleTypes.java:81`). An earlier draft of this file said
  `small_soul_fire_flame`, which does not exist in 26.2 — the only near neighbours are
  `soul_fire_flame` and `small_flame` (`:138`).
- Erasure is staged: a `minecraft:light` block appears over the mob's head, the mob vanishes
  `removal_light_delay_ticks` later, then the light fades 15 to 0. Risers are emitted with a
  particle packet of `count == 0`, the only form the client reads as a directed velocity rather
  than random offsets (`ClientPacketListener.handleParticleEvent`).
- Spawning an existing vanilla particle is a plain world method call. It needs no Mixin and no
  Fabric particle API — that API is only for registering *new* particle types.

### End crystal consequences to account for

- Explosion power is 6. Netherite block, ancient debris, and obsidian are all blast resistant
  enough to survive, so the frame comes through intact.
- It's an entity, so there is no block entity tick to piggyback on. Detection hooks the end
  crystal's entity tick (throttled) or entity-add plus adjacent block change events.
- Deactivation triggers on **entity removal**, not block break. Cover the explosion, a player
  killing it, and chunk unload.
- In the End, any end crystal heals the Ender Dragon. That is vanilla behavior and is left
  alone. Building a Mob Conduit in the End during a dragon fight works against the player.
- The `ShowBottom` NBT flag renders a bedrock slab under the crystal and syncs to vanilla
  clients. Optional cosmetic; decide once the structure is visible in game.
- Saved data is keyed by the crystal's BlockPos. Crystals do not move.

### Deliberately out of scope

- Does not block monster spawners, spawn eggs, or breeding. Mob farms inside the radius keep
  working. Blocking these would make the mod actively hostile to normal play.
- Does not affect passive or neutral mob spawns. Hostiles only.
- Does not continuously kill mobs inside the radius.
- Not a light-source replacement, not a difficulty setting, not a peaceful-mode toggle.

### Settled, do not revisit

- An earlier draft used a `minecraft:conduit` block as the center. Vanilla clients always
  render that in its inactive state, which is why it was replaced. Do not propose reverting.
- The end crystal explosion stays. See above.

---

## Hard constraint: server-side only, vanilla clients must connect

This mod is installed on the server only. Players join with an unmodified vanilla client and
must never be required to install anything.

**This means no new registry entries of any kind.** No custom blocks, items, block entities,
particles, sounds, entities, or enchantments. Anything registered will not exist on a vanilla
client and registry sync will reject the connection.

Everything the player sees must be a vanilla block, vanilla particle, vanilla sound, or plain
chat / action bar text. Mixins are fine: they are server-side and change no registries.

`src/client/` should stay **empty**. If a proposed change would require the client to have this
mod, stop and say so. Do not implement it.

## Performance is the top priority

Runtime performance outranks everything except correctness. Any workflow overhead that buys
runtime performance is pre-approved: benchmarks, profiling runs, gametests, extra abstraction,
more complex data structures. Do not ask permission for these, just do them and report numbers.

- **Never scan the world for conduits.** Detection is event-driven: validate on the end
  crystal's entity tick (throttled) or on adjacent block changes. A periodic sweep looking for
  crystals is the wrong architecture.
- **Never scan all conduits per spawn attempt.** Spawn attempts fire constantly across every
  loaded chunk. Compute the covered chunk set once at activation and index by chunk.
- **Never do per-tick radius scans.** Recompute only on activation, deactivation, or frame
  change.
- **Measure, don't assume.** When you claim something is faster, show a measurement.
- Active conduits must survive a restart. The center is a vanilla entity, so there is nothing
  of ours to attach state to. State lives in world saved data keyed by the crystal's BlockPos.
- Detection needs a Mixin on the end crystal entity's tick, since there is no block entity of
  our own to tick. Throttle it hard.
- Prefer a Fabric API hook for spawn prevention if one exists. Search for it and report what
  you searched for before proposing a Mixin.

## Configuration

Server-side only, so no client sync is needed. Plain JSON read at startup plus a reload
command. Do not add a config-library dependency without asking; Gson is already on the
classpath.

| Key | Default | Notes |
|---|---|---|
| `frame_block` | `minecraft:netherite_block` | Any vanilla block ID. Validate at load. |
| `radius_min` | `64` | Radius at the minimum frame threshold. |
| `radius_max` | `128` | Radius at full frame. 128 blocks is 8 chunks, inside default simulation distance. |
| `radius_shape` | `sphere` | `sphere` = 3D radius like vanilla's conduit; `cylinder` = same horizontal radius, full height. |
| `frame_threshold_min` | `16` | Vanilla's `MIN_ACTIVE_SIZE` (`ConduitBlockEntity.java:39`). |
| `frame_threshold_max` | `42` | Vanilla's `MIN_KILL_SIZE` (`:40`), and the geometric maximum — the shape predicate at `:141-159` yields exactly 42 positions. |
| `forcefield` | `true` | Also erase hostiles that wander in. See above. |
| `forcefield_interval_ticks` | `40` | Re-sweep cadence when `forcefield` is on. |
| `removal_drops` | `false` | Kill instead of discard, so loot and XP drop. See below. |
| `activation_sounds` / `ambient_sounds` | `true` | Layered `block.beacon.*` + `block.conduit.*`. |
| `light_base_on_activate` | `true` | Swap the obsidian under the crystal for a light block while active, restored on deactivation. |
| `hologram` | `true` | Floating status text above the crystal — a vanilla `text_display` entity, deduped by tag. |
| `removal_particle_count` | `40` | Soul fire puff per erased mob. |
| `removal_riser_count` / `removal_riser_speed` | `20` / `1.0` | Climbing soul flames; ~10-21 blocks at speed 1.0. |
| `removal_light_delay_ticks` | `10` | Light appears this long before the mob vanishes. |
| `removal_light_fade_ticks` | `60` | Fade duration, walked one light level at a time. Clamped to ≥ 15. |
| `max_concurrent_lights` | `0` | Ceiling on lights in flight; 0 means unlimited. |
| `removal_exempt_types` | wither, ender_dragon, warden, elder_guardian | Entity ids never erased. Vanilla has no boss marker, so bosses are listed explicitly. |
| `suppress_exempt_types` | `[]` | Entity ids left entirely alone — neither suppressed nor swept. |
| `suppression_feedback` | `actionbar` | How a vetoed spawn announces itself to players in range: `off`, `actionbar`, `particle`. Rate-limited per conduit. |
| `disabled_dimensions` | `[]` | Dimension ids where conduits do nothing, e.g. `["minecraft:the_end"]`. |
| `removal_budget_per_tick` | `32` | Mobs processed per tick during a sweep; the staging budget. |
| `removal_light_enabled` | `true` | Master switch for the light-flash stage of erasure. |
| `removal_particle` / `removal_secondary_particle` / `removal_riser_particle` | `soul_fire_flame` / `soul` / `soul_fire_flame` | Particle ids per effect; any `SimpleParticleType`. |
| `crystal_aura_enabled` / `_count` / `_interval_ticks` | `true` / `6` / `4` | Continuous shimmer around the crystal. |
| `crystal_aura_particle` | `trial_spawner_detection_ominous` | Never culled client-side; also sent with the server's long-reach flag. |
| `kill_plume_particle` / `kill_plume_count` | `sculk_soul` / `0` | Forcefield-kill plume off the conduit top; 0 = off. |
| `kill_beam_particle` / `kill_beam_length` | `sonic_boom` / `0` | Vertical beam per forcefield kill; 0 = off. |
| `frame_drips_enabled` / `_count` / `_interval_ticks` | `true` / `3` / `8` | Crying-obsidian-style drips off the frame. |
| `frame_drip_particle` | `dripping_obsidian_tear` | Particle id for the drips. |

- **Never hardcode the frame block.** Read it from config everywhere, including detection.
- Validate `frame_block` against the block registry at load. If it does not resolve, log an
  error and fall back to the default rather than crashing the server.
- A config reload must re-validate every known active conduit. Changing `frame_block` can
  invalidate existing structures, and stale active entries would suppress spawning with no
  visible structure causing it.
- Warn on load if `radius_max` exceeds the server's simulation distance in blocks. Beyond that
  the extra radius does nothing, because unticked chunks do not spawn mobs.
- **Frame cost note:** at the netherite block default, vanilla's 16-block activation threshold
  costs roughly 576 ancient debris. This is deliberate. Server owners who want it achievable
  can set `frame_block` to `minecraft:ancient_debris` for the same look at 1/36th the cost.

### Known behaviors and unavoidable side effects

- Vetoed spawns still consume mob-cap credits (`NaturalSpawner` runs `afterSpawn`
  unconditionally). Inherent to the `ALLOW_LOAD` hook; after a mass erasure, suppressed spawns
  briefly throttle spawning outside the radius too. Steady-state impact is negligible.
- `SpawnOrigin` records die on chunk unload, not just on restart. Spawner-farm output inside a
  radius whose chunk cycled can be swept despite the farm exemption; name-tag pen stock to be
  safe. Spawner data carrying custom NBT never gets a record at all
  (`BaseSpawner.java:159-162`).
- With `forcefield: false`, these natural-ish paths are not suppressed: zombie reinforcements,
  village sieges, nether-portal piglins (vanilla files them as `STRUCTURE`), one-time structure
  populations.
- A trap horse spawned outside the radius and triggered inside yields four persistent, tamed
  horsemen that neither the guard nor the sweep will ever touch. By design: they are
  player-triggered, persistent, and tamed.
- A light block fading in a chunk that is already unloaded when the server stops is saved with
  the chunk and never cleared. Every other stranding path is handled.
- A conduit deactivates when its own chunk unloads, but its radius can still cover chunks that
  are loaded and ticking. A player ~200 blocks out leaves roughly a 72-160 block band
  unsuppressed until they return. Self-healing: re-activation re-arms and re-sweeps.

---

## Hard rule: never recall Minecraft names from memory

Minecraft Java has been **unobfuscated since 26.1**. There are no mappings and no remapping
step. **Yarn is dead** and unsupported by Fabric; Mojang's names are the only names. Versioning
is year-based since 2026 (26.1, 26.2, 26.3, then 27.1) — there is no 1.22, and anything
referring to 1.21.x is pre-2026.

Your training data predates the deobfuscation and uses Yarn names. Those names are wrong and
the code will not compile.

Before using any Minecraft class, method, field, or signature:

1. Read it from `./.minecraft-src/` (see below), or
2. Verify it at https://mcsrc.dev

**Cite what you read.** Any time you use a Minecraft class, method, or field name — in code, in
an answer, in a plan — give the file path you read it from. An answer with no path is an answer
from memory and is not acceptable.

If you cannot verify a name, **say so and stop**. Do not guess, do not approximate, and do not
offer a name with a hedge like "it may be called". A wrong name that looks compilable is worse
than an admission that you don't know.

The same applies to Fabric API class and event names. Verify against https://docs.fabricmc.net
with the version selector set to 26.2.

Ignore any tutorial, blog post, forum answer, or code sample dated before 2026. All of them
assume obfuscated Minecraft and Yarn mappings.

## Decompiled Minecraft sources

Greppable decompiled Minecraft sources live at **`./.minecraft-src/`**, as a real
`net/minecraft/...` package tree (~7,000 `.java` files). Use it to read vanilla behavior:

```
grep -rn "class Monster" .minecraft-src/net/minecraft/world/entity/
```

Names are Mojang mappings — real class, method, and field names, not obfuscated.

### This is a snapshot of Minecraft 26.2

The tree is a **static snapshot**. Nothing in the Gradle build reads from it, updates it, or
checks it against `minecraft_version`. After **any** Minecraft version bump it will still
contain 26.2 code and will keep answering greps with classes, methods, and signatures that no
longer exist in the version you are compiling against. It fails silently — no error, no
warning, just quietly wrong answers.

**After changing `minecraft_version` in `gradle.properties`, regenerate it:**

```sh
./gradlew genSources                        # repopulates Loom's decompile cache
python3 tools/unpack-decompiled-sources.py  # rewrites ./.minecraft-src/ from that cache
```

Both steps are required. `genSources` alone is **not** enough: it writes only to Loom's cache at
`~/.gradle/caches/fabric-loom/decompile/v1.zip`, which is content-addressed (files named by
SHA-256, not by class) and therefore cannot be grepped. The unpack script is what produces the
readable tree, and it wipes the old one first so classes deleted upstream do not linger.

If you are unsure whether the tree matches the current build, just re-run both commands.

### Never commit it

`.minecraft-src/` is decompiled proprietary Mojang code. It is gitignored (`.gitignore`, under
"decompiled minecraft sources") and must stay that way. Do not commit it, do not add it to a
source set, and do not copy vanilla source files into `src/`.

---

## Commands

```
./gradlew runServer                     launch a dev dedicated Fabric server (primary test target)
./gradlew :neoforge:runServer           launch a dev NeoForge server (neoforge/run)
./gradlew build                         produce the Fabric jar in build/libs/
./gradlew mergeJar                      produce the all-platform uber-jar (build/libs/mob-conduit-<v>-all.jar)
./gradlew genSources                    repopulate Loom's decompile cache
./gradlew build --refresh-dependencies  fix Gradle/Loom cache corruption
./gradlew tasks                         list available tasks if one above is missing
```

Distribution artifacts: `build/libs/mob-conduit-<version>.jar` (Fabric),
`neoforge/build/libs/mob-conduit-neoforge-<version>.jar` (NeoForge),
`bukkit/build/libs/mob-conduit-bukkit-<version>.jar` (Paper/Spigot), and
`build/libs/mob-conduit-<version>-all.jar` (one jar that loads on all four loaders — the
release artifact). The `-dev` and `-sources` jars are build artifacts, not releases.

**In-game verification:** the dev servers run with RCON on (`rcon.password=mobconduit`,
port 25575) and `pause-when-empty-seconds=0` (26.2 pauses empty dedicated servers; without
this nothing ticks and every test looks broken). `tools/rcon.py "<command>" [port]` sends one
command; `tools/rcon-battery.py` is the checked-in state battery:

```
python3 tools/rcon-battery.py phase1 --port 25575          # build, assert, stop
python3 tools/rcon-battery.py check-persistence --store <conduits.dat|world json>
python3 tools/rcon-battery.py null-config --config <mob-conduit.json>
<restart>  python3 tools/rcon-battery.py phase2 --port 25575
```

It asserts **state** — activation, radius, base-block swap, hologram dedup, config round-trips,
and survival across a clean restart — on Fabric, NeoForge and Paper alike. It deliberately
asserts nothing about spawn suppression rates: natural spawning is player-driven and barely
runs with nobody online, so a headless verdict there would mislead. Those tests are Greg's.
Note it issues `forceload add 0 0` first, because with no player online the build chunk
unloads immediately and every check fails with "That position is not loaded".

## Layout

```
src/main/java/          loader-neutral core (Mojang-mapped) + Platform SPI + fabric/ entrypoint
src/client/java/        client-only — should stay EMPTY, see the server-side-only rule
src/main/resources/     fabric.mod.json, the shared mixins config, data
neoforge/               NeoForge module (ModDevGradle): adapter, reason-capture mixin,
                        neoforge.mods.toml, accesstransformer.cfg
bukkit/                 Bukkit module: full sibling implementation against the pure Bukkit
                        API (Paper first, Spigot-compatible, no mixins)
docs/superpowers/       specs and plans
tools/                  unpack-decompiled-sources.py, publish-*.py, rcon.py
run/                    dev world and logs, gitignored
.minecraft-src/         decompiled Minecraft, gitignored, never committed
```

**Multi-loader architecture.** The core in `src/main/java` is plain vanilla code; everything
loader-specific is behind `io.github.gregj.mobconduit.Platform` (event registration, config
dir, spawn-reason lookup). Fabric wires it in `fabric/FabricPlatform`; NeoForge in
`neoforge/NeoForgePlatform` (plus an `EntityTypeMixin` that replicates Fabric's spawn-reason
capture and an access transformer for the two `Display` methods Fabric's access wideners make
public — **`.minecraft-src` shows those as public because it was decompiled from the
AW-patched jar; do not trust it for visibility**). The Bukkit module shares no code with the
NMS tree — `org.bukkit.*` is a disjoint API — it is a sibling implementation held to the same
behavior by spec and by the shared RCON battery. Rule of thumb: NMS changes go in the core and
flow to Fabric + NeoForge; Bukkit needs the matching change in `bukkit/` — make both in the
same commit.

## Conventions

**Prefer Fabric API events over Mixins.** Only write a Mixin when no event or API hook exists.
Check Fabric API first and say what you searched for if you conclude no hook exists.

**Mixin rules.** (Fabric and NeoForge only — the Bukkit module has no mixin framework and must
stay pure-API.)
- Get the target method name and signature from `./.minecraft-src/`, never from memory.
- Prefix injected method names `mobconduit$` (no hyphen — hyphens are illegal in Java
  identifiers). Unprefixed names collide with other mods targeting the same class.
- Shared mixins (vanilla-targeted, loader-neutral) live in the core and are registered in
  `mob-conduit.mixins.json`, which both `fabric.mod.json` and `neoforge.mods.toml` declare.
  NeoForge-only mixins live in `neoforge/`'s own package and `mob-conduit.neoforge.mixins.json`.
  An unregistered Mixin silently does nothing.
- Keep Mixin bodies tiny. Call out to a normal class; do not put logic inside the Mixin.
- Mixins are the main cost of every future version bump. Fewer is better.

**Data over code.** If something can be expressed as data pack JSON, generate it with the
datagen API rather than hardcoding it in Java. Never hand-edit `src/main/generated/`.

**Access.** Need something private in vanilla? On Fabric use Class Tweakers (access widening,
interface injection, enum extension); on NeoForge add the same lines to
`neoforge/src/main/resources/META-INF/accesstransformer.cfg`. Do not use reflection. Remember
`.minecraft-src` is AW-contaminated for visibility — check the real visibility need on both
loaders.

## Version bumps

Update `minecraft_version`, `loader_version`, and `fabric_version` in `gradle.properties`, and
the Loom version in `build.gradle`. Then regenerate `.minecraft-src/` per the two commands
above. Re-verify every Mixin target against the new sources; that is where breakage lives.

Note that Loom is on `1.17-SNAPSHOT`, a moving target. If a build breaks with no local change,
suspect the snapshot before suspecting the code.

## Working style

- **Read before you write.** For anything touching Minecraft or Fabric APIs, first report what
  you found and where, then stop. Do not research and implement in one pass.
- **Show the diff before writing files.**
- Terse output. No preamble, no summary of what you just did, no praise.
- Complete the task before handing back. Don't stop at a plan when the code was asked for.
- Commit messages: one line, imperative, lowercase.
- Don't add dependencies without asking.
- Don't refactor code you weren't asked to touch.