# Tier 1 feature batch — design

Follow-up to the 2026-07-29 audit and bug-fix batch. Research grounding: the four most
borrowable, vanilla-client-safe features in the spawn-suppression category (Torchmaster,
Magnum Torch, In Control!, Ender IO Aversion Obelisk). All four are pure config + packets:
no new registries, no new mixins, vanilla clients unaffected.

## 1. Suppression feedback — `suppression_feedback` (default `"actionbar"`)

The #1 requested feature in the category: players cannot tell suppression is working.

- Values: `"off"`, `"actionbar"`, `"particle"`. Unknown values log and fall back to `"off"`.
- On every vetoed spawn (natural guard veto and the trap-horse veto), notify players inside
  the conduit's radius. Actionbar mode sends
  `ClientboundSetActionBarTextPacket` (`TitleCommand.java:70` is the vanilla usage) with
  "Mob Conduit suppressed a \<mob\>". Particle mode puffs 3 `soul_fire_flame` at the veto site.
- Rate-limited to once per 40 ticks per conduit (spawn attempts fire constantly).
- Needs the suppressing conduit, not a boolean: `ConduitStore.suppresses` refactors onto a
  new `suppressingConduit(BlockPos) -> Conduit | null`. Cooldown lives in a transient
  `Map<BlockPos, Long>` on the store, keyed by conduit position.

## 2. `/mobconduit visualize` — show the coverage edge

The other half of "I can't see the radius", solved while building.

- Arms every active conduit in the caller's dimension for 200 ticks. Reply: how many.
- New `RadiusVisualizer`, ticked from the existing `END_LEVEL_TICK` hook (gated on
  non-empty). Every 5 ticks per conduit it emits one great-circle band of `minecraft:glow`
  particles (overrideLimiter = true, `ParticleTypes.java:145`, so the ring reads at 128
  blocks), rotating through three orientations (xy, xz, yz planes) so the sphere reads in 3D.
  ~40 directed count==0 packets per band; bounded, and it expires on its own.

## 3. Per-dimension switch — `disabled_dimensions` (default `[]`)

Dimension ids where conduits do nothing (e.g. `minecraft:the_end`, where any crystal heals
the dragon anyway). Unparseable ids log an error and are ignored.

- Spawn guard: early-out before any suppression logic.
- Detector: `validate` deactivates and does not re-activate in a disabled dimension.
- `revalidate` drops conduits sitting in a newly-disabled dimension, with the same teardown
  as a broken frame (restore base, un-arm).

## 4. Mob filter — `suppress_exempt_types` (default `[]`)

Entity ids the conduit leaves entirely alone — neither suppressed nor swept. Magnum Torch /
Aversion Obelisk style selection, as pure config. Use case: phantoms, or a mob the server
wants farmable in the open. Resolved to a set at validate like `removal_exempt_types`;
checked in the guard and in `queueRemovalSweep`.

## Config and commands

`set`/`get`/`reload` pick up all three new keys automatically (Gson-tree driven). One new
subcommand: `visualize`. Permission level stays 2.

## Verification

`./gradlew build`; dev-server boot; config-validation probe (bad `suppression_feedback`
value falls back with an error in the log). Interactive rendering checks reported as
unverified if they cannot be driven headlessly.
