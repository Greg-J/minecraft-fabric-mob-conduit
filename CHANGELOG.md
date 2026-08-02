# Changelog

## 1.2.0

A full audit, seven bug fixes, and a feature pass grounded in what players ask for from
spawn-proofing mods.

### New features

- **Suppression feedback** — when the conduit eats a spawn, players in range get an action-bar
  ping (or a particle puff at the spawn site). `suppression_feedback`: `off`, `actionbar`
  (default), `particle`. Rate-limited per conduit.
- **`/mobconduit visualize`** — draws each active conduit's coverage volume in particles for
  10 seconds, so you can see the edge before committing frame blocks. Sent with the server's
  long-reach particle flag, so it actually renders from where you're standing.
- **`radius_shape`** — `sphere` (default, vanilla-conduit-like 3D radius) or `cylinder`
  (same horizontal radius, full height — the usual shape for spawn-proofing a base).
- **Status hologram** — a floating "Mob Conduit / radius N" line above the crystal while
  active. It's a vanilla `text_display` entity, so vanilla clients render it; `hologram: false`
  turns it off.
- **`disabled_dimensions`** — dimension ids where conduits do nothing, e.g.
  `["minecraft:the_end"]`.
- **`suppress_exempt_types`** — entity ids the conduit leaves entirely alone, neither
  suppressed nor swept.

### Behavior changes

- **Neutral mobs are no longer suppressed.** The guard and the sweep now share one rule —
  hostile means `Enemy` minus `NeutralMob`, plus the undead mounts. In practice: endermen and
  zombified piglins spawn and persist inside the radius (matching the documented design);
  spiders and piglins stay suppressed.
- The crystal aura defaults to `trial_spawner_detection_ominous` and all long-range effects
  are sent with the server's `overrideLimiter` flag, so they render past 32 blocks. Existing
  configs keep their written particle values.

### Bug fixes

- Changing `frame_block` on a live server now fully tears down invalidated conduits: the
  obsidian under the crystal is restored (previously replaced by a permanent invisible light
  block) and rebuilding re-arms correctly.
- `frame_block: minecraft:air` is rejected at load — it used to activate any crystal anywhere
  at full radius for free.
- Erasure light blocks no longer strand forever when a chunk unloads mid-fade; the fade
  resumes when the chunk reloads.
- Light placement can no longer synchronously load a chunk on the server thread.
- A teleported end crystal deactivates the conduit it leaves behind (within 2 seconds), and
  config reload drops conduits whose crystal is gone entirely — no more crystal-less
  suppression zones.
- Fixed a per-tick drain that stayed hot for the whole session after the last conduit
  deactivated mid-erasure.
- Fixed the visualizer pinning a dead level in memory across world restarts, and the status
  sidebar leaking into the next world on an integrated server.
- Sidebar counters, light-cap accounting, and fade-duration clamping corrected; dead code and
  stale javadocs removed.

### Compatibility

Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API 0.156.0+26.2, Java 25. Server-side only;
clients need nothing.
