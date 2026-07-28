# CLAUDE.md

Fabric mod for Minecraft. Mod ID `mob-conduit`, maven group `io.github.gregj.mobconduit`.

Versions live in `gradle.properties` — treat that file as the source of truth, not this
document:

| | |
|---|---|
| Minecraft | `26.2` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.156.0+26.2` |
| Loom | `1.17-SNAPSHOT` (resolves to a moving snapshot, not a pinned release) |
| Java | 25 |

`build.gradle` calls `splitEnvironmentSourceSets()`, so common code lives in `src/main`
and client-only code in `src/client`. `settings.gradle` pins `rootProject.name =
'mob-conduit'` to match the mod ID — it is deliberately independent of the folder name.

## Decompiled Minecraft sources

Greppable decompiled Minecraft sources live at **`./.minecraft-src/`**, as a real
`net/minecraft/...` package tree (~7,000 `.java` files). Use it to read vanilla behavior:

```
grep -rn "class Monster" .minecraft-src/net/minecraft/world/entity/
```

Names are Mojang mappings — real class, method, and field names, not obfuscated.

### This is a snapshot of Minecraft 26.2

The tree is a **static snapshot**. Nothing in the Gradle build reads from it, updates it,
or checks it against `minecraft_version`. After **any** Minecraft version bump it will
still contain 26.2 code and will keep answering greps with classes, methods, and
signatures that no longer exist in the version you are compiling against. It fails
silently — no error, no warning, just quietly wrong answers.

**After changing `minecraft_version` in `gradle.properties`, regenerate it:**

```sh
./gradlew genSources                        # repopulates Loom's decompile cache
python3 tools/unpack-decompiled-sources.py  # rewrites ./.minecraft-src/ from that cache
```

Both steps are required. `genSources` alone is **not** enough: it writes only to Loom's
cache at `~/.gradle/caches/fabric-loom/decompile/v1.zip`, which is content-addressed
(files named by SHA-256, not by class) and therefore cannot be grepped. The unpack script
is what produces the readable tree, and it wipes the old one first so classes deleted
upstream do not linger.

If you are unsure whether the tree matches the current build, just re-run both commands.

### Never commit it

`.minecraft-src/` is decompiled proprietary Mojang code. It is gitignored
(`.gitignore`, under "decompiled minecraft sources") and must stay that way. Do not commit
it, do not add it to a source set, and do not copy vanilla source files into `src/`.
