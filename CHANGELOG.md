# Changelog

## 1.3.1

Bug fixes from a full review of the multi-loader tree, verified end-to-end against real Fabric,
NeoForge and Paper servers running the packaged jar.

- **Conduits survive a clean shutdown.** Server teardown ran before the world save and emptied
  the conduit list first, so a conduit built since the last autosave was erased from saved data
  on `/stop`. In practice the conduit re-derived itself from its crystal and frame within two
  seconds of the chunk loading, so this was invisible in play — but the saved state was wrong.
- **`/mobconduit visualize` draws the coverage sphere.** Two of its three rings were collapsing
  into diagonal lines because both of their coordinates came from the same term, and the one
  real ring was too sparse to see at a 128-block radius. All three are now true great circles,
  drawn together, with spacing that scales with the radius.
- **Config keys set to `null` in the JSON are reachable again.** They vanished from
  `/mobconduit get`, `set` and tab-completion, and disappeared from the file on the next save.
  Any nulled key now falls back to its documented default.
- **Disabling a dimension no longer strands a light block and a hologram** on a conduit whose
  chunk was unloaded at the time. They stayed there permanently, with nothing able to clear them.
- **Paper / Spigot: crystals are detected however they arrive.** Only crystals a player placed
  by hand were tracked, so one summoned by a command, another plugin or a structure never formed
  a conduit until its chunk cycled.
- **Paper / Spigot: the plugin now loads through `plugin.yml`** on both platforms. The
  `paper-plugin.yml` descriptor declared strictly less while forcing a separate command
  registration path; dropping it removes that split. No change to how you use it.
- Cancelled world unloads are honoured; repeat `visualize` calls no longer stack; a mob caught
  by two overlapping conduits is only condemned once; the NeoForge build ships its icon.

## 1.3.0

**One jar, four loaders.** Mob Conduit now runs on Fabric, NeoForge, Paper and Spigot from a
single codebase — and a single `mob-conduit-1.3.0-all.jar` that loads on all of them.

- **NeoForge:** the full mod, unchanged, via a small adapter (`EntityJoinLevelEvent` is the
  same shape as Fabric's spawn hook; two mixins replicate Fabric's spawn-reason capture).
- **Paper / Spigot:** a complete sibling implementation as a plugin — no mixins needed.
  `CreatureSpawnEvent`'s richer spawn reasons mean raids, patrols, village sieges and
  nether-portal piglins are recognized explicitly on this platform. Everything carries over:
  forcefield erasure, hologram, visualize, suppression feedback, sidebar, all commands,
  `radius_shape`, `disabled_dimensions`, `suppress_exempt_types`. Paper gets Adventure action
  bars; Spigot gets the legacy fallback.
- Architecture: loader-neutral core behind a small `Platform` SPI in `src/main`, the NeoForge
  adapter module in `neoforge/`, the Bukkit plugin module in `bukkit/`.
- Behavior fixes from the port's review: tamed mounts (any `Tameable`, not just horses) are
  never swept on Bukkit; suppression feedback prints proper mob names; the Bukkit tick
  pipeline survives a per-world failure; NeoForge jar no longer mis-parses on Fabric;
  world-rename-safe persistence on Bukkit.

Same rules as ever: server-side only, nothing registered, vanilla clients just connect.
Minecraft 26.2, Java 25. NeoForge 26.2.x (beta builds) or Fabric Loader 0.19.3+/Fabric API or
any recent Paper/Spigot 26.2.

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
