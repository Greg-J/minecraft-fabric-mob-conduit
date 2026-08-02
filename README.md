# Mob Conduit

A Fabric mod for Minecraft **26.2** that adds a player-built multiblock which stops hostile mobs
spawning around it — built entirely from vanilla blocks, with no new items, blocks, or recipes.

**Server-side only.** Players join with an unmodified vanilla client and install nothing. The mod
registers no new content of any kind, so registry sync is untouched and vanilla clients connect
normally.

## What it looks like

The structure reuses the vanilla conduit's frame geometry, so it is a shape players already know:

- **Centre** — a vanilla end crystal sitting on an obsidian block. The crystal's own idle
  animation is the "powered on" indicator, which is why no client mod is needed to see the state.
- **Frame** — three orthogonal 5×5 rings of a configurable block (default
  `minecraft:netherite_block`), the same arrangement as a prismarine conduit. 42 positions in
  total.
- Frame block count sets the radius, using vanilla's own thresholds.

Unlike the vanilla conduit, there is **no water requirement** — it works in open air.

The crystal is destructible and the explosion is deliberate. Blow it up and the conduit shuts
off; place a new crystal on the obsidian to bring it back.

## What it does

- Blocks **natural** hostile spawning inside its radius. Monster spawners, trial spawners, spawn
  eggs, breeding and `/summon` all keep working, so mob farms inside the radius are unaffected.
- Passive and neutral mobs are never touched.
- On activation, hostiles already inside are erased — a light block appears over each one, it
  vanishes in a burst of soul fire that climbs 10–20 blocks, and the light fades out. Staged
  across ticks so a large sweep does not stutter.
- With `forcefield` on (the default), hostiles that wander in afterwards get the same treatment.
- While active it weeps obsidian tears off the frame and holds a shimmer of sculk souls around
  the crystal. Every particle is configurable, including the type.

Boss mobs, named mobs, and anything flagged persistent are exempt.

## Radius

Radius scales linearly with frame block count, between the two thresholds:

| Frame blocks | Radius |
|---|---|
| below 16 | inactive |
| 16 | 64 |
| 42 (full frame) | 128 |

Both thresholds match vanilla's conduit (`MIN_ACTIVE_SIZE` and `MIN_KILL_SIZE`). The radius is
spherical, measured from the crystal — or set `radius_shape` to `cylinder` for the same
horizontal radius at full height.

> **Note on cost.** At the netherite default, a full 42-block frame is roughly 1,512 ancient
> debris. This is intentional — it is meant to be an endgame answer to base spawn-proofing.
> Server owners who want it achievable can set `frame_block` to `minecraft:ancient_debris` for
> the same look at a fraction of the cost.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3+ for Minecraft 26.2 on your server.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) 0.156.0+26.2 in `mods/`.
3. Put `mob-conduit-1.2.0.jar` in `mods/`.
4. Start the server. Requires **Java 25**.

Nothing is installed on the client.

## Commands

All require permission level 2 (gamemaster).

| Command | Effect |
|---|---|
| `/mobconduit status` | List active conduits and show a live scoreboard sidebar of spawn-guard stats |
| `/mobconduit status off` | Hide the sidebar |
| `/mobconduit sweep` | Re-run the erasure across every conduit in this dimension |
| `/mobconduit reload` | Re-read the config and re-validate every conduit |
| `/mobconduit set <key> <value>` | Live-edit any config key, save it, and re-validate every conduit |
| `/mobconduit get <key>` | Print the current value of a config key |
| `/mobconduit build <pos>` | Erect a full frame, obsidian and crystal at `<pos>` — a testing aid |
| `/mobconduit visualize` | Draw every active conduit's coverage sphere in particles for 10 seconds |

The sidebar reports natural hostile spawn attempts, how many were suppressed, how many fell
outside every radius, and how many were seen while no conduit was active.

## Configuration

`config/mob-conduit.json`, created on first run. Everything is re-readable at runtime with
`/mobconduit reload` — no restart.

### Structure

| Key | Default | Notes |
|---|---|---|
| `frame_block` | `minecraft:netherite_block` | Any vanilla block id. Validated at load; falls back to the default if it does not resolve. |
| `radius_min` | `64` | Radius at `frame_threshold_min`. |
| `radius_max` | `128` | Radius at `frame_threshold_max`. 128 blocks is 8 chunks. |
| `radius_shape` | `sphere` | `sphere` = 3D radius like vanilla's conduit; `cylinder` = same horizontal radius, full height — the usual choice for spawn-proofing a base. |
| `frame_threshold_min` | `16` | Frame blocks needed to activate. |
| `frame_threshold_max` | `42` | Frame blocks for maximum radius, and the geometric maximum. |

### Behaviour

| Key | Default | Notes |
|---|---|---|
| `forcefield` | `true` | Also erase hostiles that wander in, not just those present at activation. |
| `forcefield_interval_ticks` | `40` | How often each conduit re-sweeps when `forcefield` is on. |
| `removal_drops` | `false` | Kill instead of discard, so loot and XP drop. See the warning below. |
| `removal_budget_per_tick` | `32` | Mobs processed per tick during a sweep. |
| `removal_exempt_types` | wither, ender dragon, warden, elder guardian | Entity ids never erased. Vanilla has no boss marker, so bosses are listed explicitly — extend this for modded bosses. |
| `suppress_exempt_types` | _(empty)_ | Entity ids left entirely alone — neither suppressed nor swept. |
| `suppression_feedback` | `actionbar` | How a vetoed spawn announces itself to players in range: `off`, `actionbar` or `particle`. Rate-limited to one message per conduit per 2 seconds. |
| `disabled_dimensions` | _(empty)_ | Dimension ids where conduits do nothing, e.g. `["minecraft:the_end"]`. |

### Presentation

| Key | Default | Notes |
|---|---|---|
| `activation_sounds` | `true` | Layered `block.beacon.*` and `block.conduit.*` on activate/deactivate. |
| `ambient_sounds` | `true` | Layered ambient hum every 80 ticks while active. |
| `light_base_on_activate` | `true` | Swap the obsidian under the crystal for a light block while active, restored when it shuts off. |
| `crystal_aura_enabled` | `true` | Continuous shimmer in and around the crystal while active. |
| `crystal_aura_count` | `6` | Particles per emission. |
| `crystal_aura_interval_ticks` | `4` | Lower is denser. |
| `frame_drips_enabled` | `true` | Tears weeping off the frame while active. |
| `frame_drip_count` | `3` | Frame blocks that drip per pass, out of 42. |
| `frame_drip_interval_ticks` | `8` | Lower is heavier. |
| `removal_particle_count` | `40` | Burst per erased mob. |
| `removal_riser_count` | `20` | Flames that climb out of the mob. |
| `removal_riser_speed` | `1.0` | Upward velocity per riser; ~1.0 climbs 10–21 blocks. |
| `removal_light_enabled` | `true` | Light block over each mob's head as it is erased. |
| `removal_light_delay_ticks` | `10` | How long the light shows before the mob vanishes. |
| `removal_light_fade_ticks` | `60` | Fade duration, walked one light level at a time. |
| `max_concurrent_lights` | `0` | Ceiling on lights in flight. `0` means unlimited. |
| `kill_plume_count` | `0` | Burst off the top of the conduit per forcefield kill, across the centre 3×3. `0` disables. |
| `kill_beam_length` | `0` | Column fired straight up per forcefield kill, one particle per block. `0` disables. |

### Particle types

Every effect's particle is swappable. Values are vanilla particle ids.

| Key | Default |
|---|---|
| `crystal_aura_particle` | `minecraft:sculk_soul` |
| `frame_drip_particle` | `minecraft:dripping_obsidian_tear` |
| `removal_particle` | `minecraft:soul_fire_flame` |
| `removal_secondary_particle` | `minecraft:soul` |
| `removal_riser_particle` | `minecraft:soul_fire_flame` |
| `kill_plume_particle` | `minecraft:sculk_soul` |
| `kill_beam_particle` | `minecraft:sonic_boom` |

> **Only particles that need no extra data can be named by id.** That covers most of them —
> `sculk_soul`, `end_rod`, `dragon_breath`, `sonic_boom`, `witch` and so on. It does not cover
> `dust`, `block`, `item`, or `dust_color_transition`, which need a colour, block state, or
> itemstack alongside the id. Naming one of those logs an error identifying the key and falls
> back to the default rather than failing to start.
>
> Some particles are flagged to stay visible at long range and through reduced particle
> settings; others fade out at distance. `sonic_boom` and `trial_spawner_detection_ominous` are
> long-range, `sculk_soul` is not. Worth knowing if an effect looks right up close but vanishes
> from across the base.
>
> `kill_beam_length` exists because `sonic_boom` only reads correctly as a line of single
> particles, one per block, the way the warden emits it. Scattering it like a burst produces a
> wall of white.

> **`removal_drops` and `forcefield` compound.** Separately, `removal_drops` gives a one-off
> payout when a conduit activates. Together with `forcefield` you have built a permanent mob
> grinder that streams loot and XP into the sphere indefinitely, and dropped items persist for
> five minutes. That may be what you want — just know it is a different thing from a one-time
> activation bonus.

If `radius_max` exceeds the server's simulation distance, a warning is logged at startup:
unticked chunks do not spawn mobs, so the extra radius does nothing.

## How it works

- **Detection is event-driven.** Nothing ever scans the world looking for conduits. A Mixin on
  the end crystal's `tick()` re-validates the frame every 40 ticks, offset by entity id so
  crystals do not all re-scan on the same tick.
- **Spawn suppression uses a Fabric API hook, not a Mixin.** `ServerEntityEvents.ALLOW_LOAD`
  carries the spawn reason and can cancel the load, which covers the whole feature without
  touching `NaturalSpawner`.
- **Covered chunks are computed once** at activation and indexed by packed chunk key, so a spawn
  attempt costs one long lookup rather than a scan over conduits.
- **State lives in world saved data** keyed by the crystal's position, so active conduits survive
  a restart. The centre is a vanilla entity, so there is nothing of ours to attach state to.

Two Mixins — one on `EndCrystal#tick` for detection, one on `Mob#finalizeSpawn` to backfill
spawn reasons the Fabric hook cannot see. That is the entire surface area against Minecraft
internals.

## Building from source

```sh
./gradlew build        # jar lands in build/libs/
./gradlew runServer    # dev server
```

Requires JDK 25. Minecraft 26.2 is unobfuscated, so there is no remapping step and no Yarn.

## Releasing

Publishing to Modrinth and CurseForge is automated. Bump `mod_version` in `gradle.properties`,
then push an annotated tag:

```sh
git tag -a v1.0.1 -m "Fix frame detection across chunk borders"
git push origin v1.0.1
```

The tag annotation becomes the changelog on both sites, so write it for players rather than for
the commit log. CI builds the jar, checks it, and uploads it.

Guards that will stop a bad release before anything is published:

- Tag version must match `mod_version` in `gradle.properties`
- The built jar's `fabric.mod.json` must declare that same version
- The version must not already exist on Modrinth

The workflow can also be run by hand from the **Actions** tab, with a release-channel picker
and a dry-run option. That is the way to retry a failed upload without re-tagging.

Each site is skipped if its project id secret is unset, so one can be added without the other.

To publish from your own machine instead:

```sh
MODRINTH_TOKEN=... MODRINTH_PROJECT_ID=tBSSIv55 \
JAR_PATH=build/libs/mob-conduit-1.0.0.jar \
MOD_VERSION=1.0.1 MC_VERSION=26.2 CHANGELOG="..." \
DRY_RUN=true python3 tools/publish-modrinth.py
```

`tools/publish-curseforge.py` takes the same variables with `CURSEFORGE_` names. Set
`DRY_RUN=false` to actually upload. Both scripts use only the standard library.

### A note on CurseForge

CurseForge identifies Minecraft versions, loaders and Java versions by opaque numeric ids rather
than by name, and several entries can share a name across version types. Minecraft 26.2 exists
twice: id 16498 under the Java Edition type, and 16500 under another. The script resolves ids at
runtime and prefers the type whose slug is `minecraft-<version>`, so bumping
`minecraft_version` does not silently upload against the wrong one.

It also rejects an upload carrying no entry from the Environment group, with `errorCode 1021`.
This mod uploads as `Server` (id 9639).

Its upload API can only add files to a project that already exists. Create the project on the
site first, then add its numeric id as the `CURSEFORGE_PROJECT_ID` secret.

Unlike Modrinth, CurseForge has no way to check whether a version is already published, so the
duplicate guard does not cover it. Re-running a release will upload a second file.

Credentials live in repository secrets, never in the repo.

## License

CC0-1.0. See [LICENSE](LICENSE).
