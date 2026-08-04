# Multi-loader spec: NeoForge + Bukkit ports — 2026-08-02

Goal: one codebase, three platforms — Fabric (existing), NeoForge (adapter, same NMS
code), Bukkit (Paper + Spigot plugin, pure `org.bukkit.*` API). One uber-jar that loads
everywhere, plus per-platform jars. No new runtime dependencies for users on any platform.

## 1. Platform SPI (in `src/main/java`, the existing NMS tree)

Everything loader-specific moves behind a small interface, `Platform`:

- `Path configDir()`
- `void registerSpawnGuard(SpawnGuard)` — veto hook with `(Entity, ServerLevel, reason,
  loadedFromDisk) -> boolean allow`
- `void registerEntityUnload(BiConsumer<Entity, ServerLevel>)`
- `void registerEndLevelTick(Consumer<ServerLevel>)`, `registerEndServerTick(Consumer<MinecraftServer>)`
- `void registerServerStarted / registerServerStopping(Consumer<MinecraftServer>)`
- `void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>>)`
- `EntitySpawnReason spawnReason(Entity)` — Fabric reads Fabric API's `EntityLoadData`;
  NeoForge reads its own reason-capture map (see §3).

`MobConduit.onInitialize()` becomes `MobConduit.init(Platform)`. The Fabric `ModInitializer`
stays in the Fabric compile only; loader-specific files live in per-platform source sets.

## 2. NeoForge module (`neoforge/`)

- ModDevGradle 2 (official, stable), NeoForge `26.2.0.40-beta`, Mojang-mapped — same names as
  the main tree, so all 16 NMS classes compile unchanged.
- Compiles `src/main/java` **minus** the Fabric-only files, plus its own:
  - `MobConduitNeoForge` — `@Mod("mobconduit")` (NeoForge modids disallow hyphens), calls
    `MobConduit.init(new NeoForgePlatform(container.getEventBus... ))`.
  - `NeoForgePlatform implements Platform` — `EntityJoinLevelEvent` (cancellable;
    `loadedFromDisk()` — Fabric's own tracker calls it "basically identical" to ALLOW_LOAD),
    `EntityLeaveLevelEvent`, `LevelTickEvent.Post`, `ServerTickEvent.Post`,
    `ServerStartedEvent`/`ServerStoppingEvent`, `RegisterCommandsEvent`,
    `FMLPaths.CONFIGDIR`.
  - Two mixins replicating Fabric's spawn-reason plumbing: capture in
    `EntityType.create(Level, EntitySpawnReason)` (stash reason in a synchronized
    `WeakHashMap` — the same pattern `SpawnOrigin.BACKFILLED` uses), read by
    `NeoForgePlatform.spawnReason`.
- `META-INF/neoforge.mods.toml`: modid `mobconduit`, `[[mixins]]` pointing at the shared
  `mob-conduit.mixins.json` plus `mob-conduit.neoforge.mixins.json`.
- Dev server: `neoforge/run`, eula + `pause-when-empty-seconds=0` + rcon enabled, so the
  standard RCON battery runs unmodified.

## 3. Bukkit module (`bukkit/`)

Pure API, no NMS, no mixins. One plugin jar targeting Paper first, Spigot-compatible.
Compile against `paper-api` 26.2; **never import `io.papermc.*`** in shared paths — Paper-only
niceties (Adventure action bar) go behind a runtime-checked adapter so the jar also loads on
Spigot.

Mechanism mapping (verified against Paper/Spigot javadocs and paper-server sources):

| Fabric mechanism | Bukkit mechanism |
|---|---|
| `EndCrystalMixin` tick detection | `EntityPlaceEvent` (crystal placed, `EndCrystalItem.java.patch:8`) + `EntitiesLoadEvent` (chunk reload rediscovery) + throttled `BukkitScheduler` re-validation of the tracked set |
| ALLOW_LOAD spawn veto | `CreatureSpawnEvent#setCancelled` — richer reasons than vanilla (`NATURAL`, `PATROL`, `RAID`, `VILLAGE_INVASION`, `NETHER_PORTAL`, `REINFORCEMENTS`, `SLIME_SPLIT`, `TRAP`, `SPAWNER`, `TRIAL_SPAWNER`, `SPAWNER_EGG`, `BREEDING`, `COMMAND`, conversions). Closes the Fabric-side raid/siege/portal "leaks" on this platform. `CHUNK_GEN` is deprecated-for-removal on Paper — treat as natural but don't depend on the constant |
| `SpawnOrigin` bookkeeping | Paper `Entity#getEntitySpawnReason()` where non-null; otherwise the same rules as null-reason today |
| `ENTITY_UNLOAD` deactivation | `EntityRemoveEvent` (`Cause.DEATH/EXPLODE/UNLOAD`, both platforms) |
| END_LEVEL_TICK pipeline | `BukkitScheduler` 1-tick task, iterating worlds, gated the same way |
| SavedData persistence | JSON file in `getDataFolder()` per world (same codec shape) |
| Long-reach particles | `World.spawnParticle(..., force=true)` = overrideLimiter 512-block gate |
| Light blocks | `Material.LIGHT` + `Levelled` block data |
| Hologram | `world.spawn(loc, TextDisplay.class)` + scoreboard tags (same tag + dedup design) |
| Action bar feedback | Adventure on Paper (runtime-detected), legacy `spigot().sendMessage(ACTION_BAR,…)` on Spigot |
| StatusBoard | `org.bukkit.scoreboard` API |
| Commands | `plugin.yml` + `CommandExecutor` + `TabCompleter` (same subcommands) |
| Config | same Gson schema, same keys, `mob-conduit.json` in the plugin folder |

Only `plugin.yml` in the jar. A `paper-plugin.yml` was shipped initially, but it declared
strictly less (no commands, no permissions) while forcing Paper onto the paper-plugin path,
which never reads plugin.yml's commands section — costing a command-map registration branch, a
`Command` subclass and a programmatic permission node to undo. The plugin declares no
bootstrapper, loader or library, so it used nothing paper-plugins provide. Dropped 2026-08-03;
verified on a real Paper 26.2 server, which loads it as a Bukkit plugin and registers the
command from plugin.yml. No Folia support declared.

## 4. Build layout

- Root project (existing Loom build) stays the Fabric jar, untouched pipeline.
- `neoforge/` subproject: MDG, compiles the shared NMS tree (Fabric files excluded) + adapter.
- `bukkit/` subproject: plain `java-library`, paper-api + spigot-api provided deps.
- `mergeJar` task: one uber-jar = shared NMS classes + both NMS entrypoints + bukkit classes +
  all three descriptors (`fabric.mod.json`, `META-INF/neoforge.mods.toml`, `plugin.yml`) +
  both mixin configs + the access transformer. Per-platform jars still built.
- `settings.gradle` includes the two new subprojects.

## 5. Verification

- Fabric: the full RCON battery re-run (regression).
- NeoForge: dev server, same battery (build/activate/hologram/teleport/frame_block/
  disabled_dimensions/zombie-sweep) minus Bukkit-only notes.
- Paper: downloaded `paper-26.2-87.jar`, same battery adapted to plugin commands.
- Adversarial review agent over the full diff before the final commit.

## 6. Explicitly out of scope

- Folia, old Forge, Quilt, Sponge.
- Version bump / tagging / publishing — that waits for user testing.
- Sharing code between the NMS tree and the Bukkit tree beyond small pure-Java helpers —
  the APIs are disjoint; the Bukkit module is a sibling implementation, not a wrapper.
