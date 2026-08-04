# Branding assets

`icon-1024.jpg` is the master artwork. Everything else is derived from it:

- `src/main/resources/assets/mob-conduit/icon.png` — 256x256, the in-jar icon referenced by
  `fabric.mod.json` and `neoforge.mods.toml`.
- The Modrinth and CurseForge project icons, which want at least 512px.

Keep the master here rather than regenerating it by upscaling the 256px PNG.
