# Bug-fix batch — design

Source: four-agent audit on 2026-07-29 (implementation inventory, logic/state audit,
vanilla-interaction audit against `.minecraft-src/`, mod-landscape research). Every file:line
below was verified by an audit agent against the code or the 26.2 decompiled sources; re-verify
any line that drifted before editing.

Scope: seven bugs, three one-line nits, and the doc drift AGENTS.md requires us to keep
current. Feature work (Tier 1 batch) is a separate spec.

---

## 1. `revalidate()` drops conduits with no teardown

`ConduitStore.java:428-431` — a conduit invalidated by a config change (`/mobconduit set
frame_block`, `reload`) is dropped without `restoreBase` and without `armedThisSession.remove`.
Effects: the obsidian under the crystal is permanently replaced by an invisible light block,
and a later rebuild re-activates silently with no arming sweep.

**Fix:** in the drop branch of `revalidate`, call `restoreBase(level, pos)` (the chunk is
loaded — the `isLoaded` check at `:420` already passed) and `armedThisSession.remove(pos)`,
mirroring `deactivate` (`:215-236`).

## 2. `frame_block: minecraft:air` is accepted

`ModConfig.java:354-368` — `resolveBlock` checks only that the id exists. Air resolves, the 42
frame positions are air by default, so any crystal anywhere activates at full frame for free.

**Fix:** reject `state.isAir()` default block states in `resolveBlock` (covers `air`,
`cave_air`, `void_air`) with the existing log-and-fall-back path.

## 3. Chunk unload mid-erasure strands light blocks

`RemovalEffects.java:123-129` — fading (and armed) lights are dropped from tracking when
`isOurLight` returns false for an unloaded chunk, but the block is already saved in the chunk.
`clearLight`/`clearAll`/`forget` are all `isLoaded`-gated, so nothing ever removes it:
permanent invisible light.

**Fix:** keep tracking entries across unload instead of dropping them. Entries store
remaining fade state; when the chunk is loaded again the fade resumes. Hook
`ServerChunkEvents.CHUNK_LOAD` (verify exact event name against Fabric API 0.156.0 docs
before use) to resume, or simpler: leave entries in the map and let `tickFading` skip
unloaded positions without removing them (check memory bound: entries are tiny; cap at a
few thousand and drop-oldest with a warning). Prefer the no-new-hook option if it stays
simple. Update the class javadoc (`:30-32`), which currently claims stranded lights are
impossible.

## 4. `placeLight` can synchronously load a chunk

`RemovalEffects.java:193` — `level.isEmptyBlock(pos)` runs without an `isLoaded` guard;
every other block read in the mod guards first.

**Fix:** `if (!level.isLoaded(pos)) return null;` at the top of `placeLight`.

## 5. `deactivate` never re-syncs `levelsWithEffects`

`ConduitStore.java:227-231` — after the last conduit deactivates mid-erasure,
`anyPendingEffects()` stays true for the session and `onLevelTick` does store lookups and two
drain calls for every level every tick.

**Fix:** call `syncEffectsFlag()` after `effects.clearAll(level)` in `deactivate`, as
`forget` already does (`:460-461`).

## 6. Phantom conduit after same-level crystal teleport

A `/tp` of an end crystal fires no `ENTITY_UNLOAD`; the old position stays registered and
indexed, and `revalidate` keeps it because the frame is intact. Nothing ever checks that the
crystal still exists.

**Fix, two parts:**
- Track crystal→position: a `WeakHashMap<EndCrystal, BlockPos>` (same pattern as
  `SpawnOrigin.BACKFILLED`) written on activation. In `ConduitDetector.validate`, if the map
  holds a different pos for this crystal, `deactivate` the old one first. Covers teleport
  within ≤ 40 ticks.
- In `ConduitStore.revalidate`, for conduits in loaded chunks, check an `EndCrystal` exists
  at `pos` (entity query, loaded chunk only); drop (with the fix-#1 teardown) if absent.
  Covers exotic crystal loss on reload/set. Verify the entity-lookup call against
  `.minecraft-src` (`ServerLevel`/`EntityGetter`) during implementation.

## 7. Neutral mobs suppressed, contradicting the spec

`MobConduit.java:88` — `instanceof Enemy` catches `EnderMan` and `ZombifiedPiglin`
(`NeutralMob` implementers), so conduits suppress natural enderman (End) and zombified
piglin (nether) spawns. Decision (user, 2026-07-29): match the spec — neutrals unaffected.

**Fix:** one shared predicate, used by both the spawn guard and the sweep filter
(`ConduitStore.queueRemovalSweep`, which filters `Enemy || UNDEAD_MOUNTS`):
`suppressible = (entity instanceof Enemy || type in UNDEAD_MOUNTS) && !(entity instanceof NeutralMob)`.
During implementation enumerate `NeutralMob` implementers in 26.2 from `.minecraft-src`
(grep `implements NeutralMob` / `extends.*NeutralMob`) and sanity-check the list — expected
members: enderman, zombified piglin, piglin(?), spider(?), wolf, bee, dolphin, goat, llama,
polar bear, panda, fox. Wolves/bees/etc. are not `Enemy` so they're already unaffected; the
predicate only changes behavior for `Enemy ∩ NeutralMob`. Update the `Hostile mobs only`
section of AGENTS.md to state the rule precisely: `Enemy` minus `NeutralMob`, plus the
undead mounts.

## Nits riding along (one-liners)

- `RemovalEffects.lightCount()` (`:93-95`): count only entries with `lightPos != null` so
  `max_concurrent_lights` reflects real lights.
- `removal_light_fade_ticks`: clamp to ≥ 15 in `ModConfig.validate` (values 1–14 currently
  all behave as 15).
- Trap-horse veto (`MobConduit.java:70`): also increment `HOSTILE_OTHER_REASON` so the
  sidebar counters stay consistent.
- `crystalAura` default vs javadoc: the javadoc (`ConduitParticles.java:30-31`) argues the
  aura needs `overrideLimiter = true` for distance visibility, but the default `sculk_soul`
  has it false. Change the default to `minecraft:trial_spawner_detection_ominous`
  (`ParticleTypes.java:156`, `overrideLimiter = true`) — the particle the comment describes.
  Affects fresh configs only; note in commit message.

## Doc drift (AGENTS.md requires docs track implementation)

- AGENTS.md config table: add the 18 implemented-but-undocumented keys (exempt types,
  budget, light enable, 7 particle keys, aura/plume/beam/drip keys) — source of truth is
  `ModConfig.java:53-142`.
- AGENTS.md: document as known behavior — vetoed spawns still consume mob-cap credits
  (inherent to `ALLOW_LOAD`); `SpawnOrigin` records die on chunk unload, so chunk-cycled
  spawner-farm output inside the radius can be swept; suppression-only leaks
  (reinforcements, sieges, portal piglins) when `forcefield: false`; trap horses triggered
  inside but spawned outside are untouchable by design.
- README.md: add `set`/`get`/`sweep`/`build` to the commands table; fix "a single Mixin"
  (two); fix `removal_exempt_types` default (4 mobs, not 2); fix install jar version.
- MODRINTH.md: update the commands list.
- Javadoc: `EndCrystalMixin.java:11` ("the only Mixin" — false); `MobConduitCommand.java:20-21`
  (lists only reload/status).
- Dead code: delete `SpawnStats.reset()`, `ConduitStore.hasPendingEffects()`,
  `MobConduit.id()`, and make `ConduitStore.activate` return void (only caller ignores it;
  fix the stale javadoc at `:116-119`).

## Deferred (not in this batch)

- `StatusBoard` static staleness on world switch and pre-existing-objective hijack — minor,
  needs its own thought.
- Tier 1 feature batch: suppression feedback, `/mobconduit visualize`, per-dimension config,
  mob filter lists. Separate spec after this ships.

## Verification

- `./gradlew build` green.
- In-game (`./gradlew runServer`): activate a conduit; `/mobconduit set frame_block
  minecraft:ancient_debris` → obsidian restored, no phantom light; rebuild with new frame →
  activation sound + sweep run. `/mobconduit set frame_block minecraft:air` → rejected with
  fallback. End conduit: endermen spawn naturally inside radius. Teleport an active crystal →
  old zone dies within 2 s.
