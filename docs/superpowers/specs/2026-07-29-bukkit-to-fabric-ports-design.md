# Bukkit/Sponge → Fabric Server-Side Ports — Market Research & Candidate Designs

Date: 2026-07-29
Status: research complete, awaiting review
Companion to: `2026-07-29-mod-ideas-per-category-design.md`

**Goal.** Identify beloved Bukkit/Sponge plugin functionality worth re-implementing as
server-side-only Fabric mods, and design each port around a two-mode architecture:

- **Baseline:** server-side only, vanilla clients connect and get a good experience (the Mob
  Conduit constraint, loosened from "zero registries" to "zero *required* client installs" —
  Polymer-style packet fakery is in bounds).
- **Enhanced:** if the client also has the mod (detected via a networking handshake), richer
  features unlock per player.

**Method.** Four parallel research streams, sources cited inline:

1. **Bukkit canon leaderboard** — Modrinth API (`project_type:plugin`), Spiget API (SpigotMC
   counts), cfwidget (CurseForge/BukkitDev), secondary sources where platforms block scraping.
   Combined = Spigot + CurseForge + Modrinth; direct-download sites not counted (real totals
   higher). Premium numbers are Spigot purchases only.
2. **Fabric gap map** — Modrinth API v2 per function (`categories:fabric`), download counts and
   last-update dates; verdicts PORTED / PARTIAL / UNPORTED.
3. **Demand evidence** — Reddit via PullPush API. **Coverage ends ~2025-05-18**; post-2023 scores
   are unreliable in the archive (score=1), so 2024+ engagement is cited via comment counts.
4. **Technical landscape** — Polymer/sgui/Nucleoid documentation, Fabric networking docs,
   minecraft.wiki protocol pages.

**How to read this doc.** Section 1 maps the canon (what players loved). Section 2 maps what
Fabric already has (what's *not* an opening). Section 3 is the product: ten ranked port
candidates with dual-mode designs. Section 4 is the shared architecture. Section 5 is the
shortlist.

---

## Section 1 — The Bukkit canon leaderboard

Per category: the plugins that defined Bukkit server culture, with approximate combined downloads
and health. † = dead/abandoned.

### Building / world editing

| Plugin | Downloads | State |
|---|---|---|
| WorldEdit | ~33M+ ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/worldedit), [Modrinth](https://modrinth.com/plugin/worldedit)) | Healthy, 26.2, also ships as a mod — no gap |
| WorldGuard | ~10.5M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/worldguard)) | Healthy, 26.2, Bukkit-only — **gap on Fabric** |
| FAWE | ~1.9M ([Spigot](https://www.spigotmc.org/resources/13932/)) | Healthy |
| VoxelSniper † | 1.37M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/voxelsniper)) | Dead 2017 |
| Axiom | 220K ([Modrinth](https://modrinth.com/plugin/axiom-paper-plugin)) | Healthy but **Paper-locked** |

### Admin / protection / logging / permissions

| Plugin | Downloads | State |
|---|---|---|
| PermissionsEx † | 14.1M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/permissionsex)) | Dead ~2016; LuckPerms won |
| LuckPerms | ~11.1M ([Spigot](https://www.spigotmc.org/resources/28140/), [Modrinth](https://modrinth.com/plugin/luckperms)) | Healthy; **has a Fabric build** |
| CoreProtect | ~1.7M ([Spigot](https://www.spigotmc.org/resources/8631/)) | Healthy, 26.1, Bukkit-only |
| NoCheatPlus † | ~2.2M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/nocheatplus)) | Dead 2018; Grim won |
| LWC † | ~2.1M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/lwc)) | Dead lineage; Bolt (5.2K) is the tiny heir |
| GriefPrevention | ~675K ([Spigot](https://www.spigotmc.org/resources/1884/)) | Healthy |
| GrimAC | ~875K ([Spigot](https://www.spigotmc.org/resources/99923/), [Modrinth](https://modrinth.com/plugin/grimac)) | Healthy; **has a Fabric build** |
| AdvancedBan † | 811K ([Spigot](https://www.spigotmc.org/resources/8695/)) | Dead 2020; LiteBans is paid |
| Lands / LiteBans / GriefDefender | premium | Healthy, paid |

### Economy / shops

| Plugin | Downloads | State |
|---|---|---|
| Vault | ~11.5M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/vault)) | Stale 2020, still the API standard; VaultUnlocked (111K) is the maintained fork |
| ChestShop | ~6.0M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/chestshop)) | Healthy, 26.2 |
| EconomyShopGUI | 2.31M ([Spigot](https://www.spigotmc.org/resources/69927/)) | Healthy |
| Jobs Reborn | ~1.0M ([Spigot](https://www.spigotmc.org/resources/4216/)) | Healthy |
| Shopkeepers | 766K ([Spigot](https://www.spigotmc.org/resources/80756/)) | Healthy |
| AuctionHouse | 766K ([Spigot](https://www.spigotmc.org/resources/61836/)) | **Stale since 2024-01** |
| QuickShop-Hikari | 221K ([Modrinth](https://modrinth.com/plugin/quickshop-hikari)) | Healthy |

### Essentials / infrastructure

| Plugin | Downloads | State |
|---|---|---|
| SkinsRestorer | ~20M ([Spigot](https://www.spigotmc.org/resources/2124/)) | Spigot's #1 ever; offline-mode niche |
| Essentials (original) † | 10.9M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/essentials)) | Dead 2015; EssentialsX won |
| EssentialsX | ~6.5M ([Spigot](https://www.spigotmc.org/resources/9089/)) | Healthy |
| Multiverse-Core | ~9.3M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/multiverse-core)) | Healthy, 26.2 |
| Chunky | ~16.1M ([Modrinth](https://modrinth.com/plugin/chunky)) | Healthy, ships for Fabric too |
| ProtocolLib / ViaVersion / PlaceholderAPI / TAB / DeluxeMenus | 3.4M / 10.8M / 2.4M / 1.9M / 870K | Healthy infrastructure |
| HolographicDisplays † | 3.1M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/holographic-displays)) | Stale; DecentHolograms (923K) / FancyHolograms (Paper-only) succeeded |
| NametagEdit † | 2.2M ([Spigot](https://www.spigotmc.org/resources/3836/)) | Stale 2023-12 |
| GSit | 2.29M ([Spigot](https://www.spigotmc.org/resources/62325/)) | Healthy |

### Minigames & lobbies

| Plugin | Downloads | State |
|---|---|---|
| MobArena | ~1.3M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/mobarena)) | Alive (2024-10) |
| Screaming BedWars | 503K ([Spigot](https://www.spigotmc.org/resources/63714/)) | Healthy, 26.2 |
| MinigamesLib † + instancelabs SkyWars † | 458K + 439K ([ML](https://www.spigotmc.org/resources/23844/)) | **Dead 2017** |
| instancelabs BedWars † / Bedwars-Rel † | 386K / 374K | **Dead 2017** |
| SkyWars (daboigif) † | 301K ([Spigot](https://www.spigotmc.org/resources/167/)) | Dead 2018 |
| BedWars1058 | 262K+ ([Spigot](https://www.spigotmc.org/resources/bedwars1058-opensource.97320/history)) | Open-sourced 2021; pre-removal count lost |
| KitPvP / Duels | 373K / 260K | KitPvP alive; Duels stale |
| plugily suite (Murder Mystery, Build Battle, Village Defense) | 98K / 72K / 51K | Healthy |
| TNTRun_reloaded | 105K ([Spigot](https://www.spigotmc.org/resources/53359/)) | Healthy |
| **Kart racing: Storm345's MarioKart †, MobRacers †, MineKart †** | 18K+ ([MarioKart](https://dev.bukkit.org/projects/mariokart), [MobRacers](https://www.spigotmc.org/resources/20626/), [MineKart](https://github.com/CodingBadgers/MineKart)) | **All dead — no living kart racer on any platform** |
| Party games / queue+map-rotation systems | <3K each | **No public flagship exists — network-proprietary** |
| Lobby cores + cosmetics: DeluxeHub / SuperLobbyDeluxe / GadgetsMenu / UltraCosmetics | 731K / 429K / 527K / 393K | Healthy |
| ASkyBlock † → BentoBox / SuperiorSkyblock2 / IridiumSkyblock | 931K † / active | SkyBlock cores alive |

### Social / RPG

| Plugin | Downloads | State |
|---|---|---|
| Citizens | ~5.2M+ ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/citizens)) | **Went premium ($19.99) on Spigot in 2025** — grievance opening |
| Factions (MassiveCraft) † | ~5.4M ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/factions)) | Dead 2018; FactionsUUID survives as a $16 paid fork |
| mcMMO | ">3M" (vendor claim) ([mcmmo.org](https://mcmmo.org/)) | Now premium; Aurelium Skills (~910K) is the free heir |
| Quests (PikaMug) / BetonQuest | ~450K / ~91K | Both healthy |
| Towny | several hundred K ([Modrinth](https://modrinth.com/plugin/towny)) | Healthy |
| MythicMobs | ~480K+ free ([Spigot](https://www.spigotmc.org/resources/5702/)) | Healthy |
| Parties / Marriage Master | 190K / 135K | Healthy |
| ModelEngine R4 | premium | **Paper-locked** |
| Typewriter | 141K ([Modrinth](https://modrinth.com/plugin/typewriter)) | **Paper-locked** |

### Cosmetics / fun

| Plugin | Downloads | State |
|---|---|---|
| LibsDisguises / iDisguise / Morph | 913K / 384K / 429K | Healthy |
| EchoPet † | 891K ([CF](https://www.curseforge.com/minecraft/bukkit-plugins/echopet)) | Dead 2015; MyPet (285K) covers |
| PlayerParticles | ~156K ([Spigot](https://www.spigotmc.org/resources/40261/)) | Healthy |
| ImageOnMap † | 650K | Stale 2022; Custom Images (479K) / ImageFrame (224K) succeeded |
| Emotecraft | 6.7M ([Modrinth](https://modrinth.com/plugin/emotecraft)) | Healthy — mod+plugin dual distribution |

### World management / chat / maps / analytics / monetization

| Plugin | Downloads | State |
|---|---|---|
| Multiverse-Core | ~9.3M | Healthy (above) |
| DiscordSRV | ~1.29M ([Spigot](https://www.spigotmc.org/resources/18494/)) | Healthy |
| Dynmap / BlueMap / squaremap | ~4.5M / 427K / 118K | BlueMap+squaremap have current Fabric builds; Dynmap lags 26.x |
| spark / Plan | 19.1M / 133K | Both have Fabric builds |
| NuVotifier † / VotingPlugin | 181K / 220K | NuVotifier stale 2021; Votifier protocol still the standard |
| CrazyCrates / ExcellentCrates / CratesPlus † | 300K / 624K / 818K † | Crates = default monetization loop |
| Tebex | n/a | Webstore infrastructure |

**Sponge note:** Sponge's native catalog is thin — GriefDefender is its flagship, and Nucleus
(essentials) is [officially discontinued](https://github.com/NucleusPowered/Nucleus). Nearly
everything else on Sponge is a cross-platform port (Chunky, TAB, Plan, squaremap).

---

## Section 2 — The Fabric gap map

Verdicts per classic plugin function (Modrinth API, 2026-07-29; downloads are Modrinth-only and
exclude CurseForge).

| Function | Bukkit canon | Best Fabric equivalent(s) | Verdict |
|---|---|---|---|
| Block logging + rollback | CoreProtect | Ledger (287k, 26.2) + Ledger Databases + Watson | **PORTED** — but rollback "a big hit and miss" ([source](https://www.reddit.com/r/admincraft/comments/1k77vxa/)) |
| Player claims | GriefPrevention/Lands | Open Parties and Claims (19.5M!), Flan (223k), GOML ReServed | **PORTED** |
| Admin flag regions | WorldGuard | Leukocyte (9k), Orbis (1.5k, new) | **PARTIAL** |
| Permissions | LuckPerms | LuckPerms itself (2.35M) + Player Roles | **PORTED** — but few mods consume it ([admincraft](https://www.reddit.com/r/admincraft/comments/1iypmil/)) |
| Economy + shops + AH | Vault/ChestShop/AuctionHouse | Numismatic Overhaul (3.6M, ≤1.21.1), Impactor (673k, ≤1.21.1), Lightman's (Fabric second-class); every AH mod abandoned | **PARTIAL/UNPORTED** |
| Essentials bundle | EssentialsX | Essential Commands (445k, 26.2), Fuji (83k, 26.2) | **PORTED** |
| Minigames + lobby/queue | BedWars/SkyWars/party | Nucleoid Plasmid + ~50 games (GitHub-only, 3.3k on Modrinth); Minimega (187k) | **UNPORTED as a product** |
| Skills/RPG | mcMMO/Aurelium | Pufferfish's Skills (3.5M, 26.2) | **PORTED** |
| Towns/nations | Towny/Factions | ickerio's Factions (31k, ≤26.1.1) | **UNPORTED** |
| NPCs + quests | Citizens/BetonQuest | Taterzens (128k, ≤1.21.6 stale); no server-side quest engine | **PARTIAL** |
| Disguises/cosmetics/pets | LibsDisguises/GadgetsMenu | DisguiseLib (Modrinth dead at 1.19.3; GitHub fork alive); no wardrobe/pets/trails | **PARTIAL/UNPORTED** |
| Multi-world | Multiverse | Fantasy (library-only); Multiworld mod (67k) | **PARTIAL** |
| Chat + Discord | DiscordSRV/chat plugins | Styled Chat (378k) + Placeholder API (53.7M) + bridges (256k) + BanHammer | **PORTED** |
| Web maps | Dynmap/BlueMap | BlueMap (427k, 26.2), squaremap (118k, 26.2), Dynmap (≤1.21.11) | **PORTED** |
| Analytics/profiling | Plan/spark | spark (19.1M), Plan (35k) — both native | **PORTED** |
| **Votes + crates** | NuVotifier/CrazyCrates | **nothing** | **UNPORTED** |
| Anti-cheat | Spartan/Matrix | GrimAC (556k, 26.2) — same incumbent | **PORTED** |
| Virtual storage GUIs | /ec, /back, backpacks | Server Backpacks (32k) + essentials commands | **PARTIAL** |
| Admin scripting | Denizen | KubeJS (18.2M), Carpet/Scarpet (9.4M) | **PORTED** |
| Holograms | DecentHolograms | HoloDisplays (14k, 26.2, right architecture) | **PARTIAL** |
| Map-art images | ImageOnMap | Image2Map (351k, 26.2) | **PORTED** |

**Ecosystem note.** Patbox's stack (Polymer 3.4M, Text Placeholder API 53.7M, Styled Chat,
BanHammer, sgui) and the NucleoidMC org (Plasmid, Fantasy, Leukocyte, Player Roles, DisguiseLib
fork) are the substrate for half this map — but Nucleoid ships via GitHub/nucleoid.xyz, invisible
on Modrinth, with **zero r/admincraft mentions ever**. The discoverability vacuum is the opening.

**Demand evidence (Reddit).** The Fabric-vs-Paper framing is consistent across five years: Paper
wins *because of the plugin catalog*
([41↑](https://reddit.com/r/admincraft/comments/wlmiqf/fabric_vs_paper/),
["how many of the most basic plugins are missing"](https://reddit.com/r/admincraft/comments/ufyi25/is_fabric_actually_feasible_for_a_public_smp/),
[34-plugin replacement list](https://reddit.com/r/admincraft/comments/18dvvd2/i_want_to_switch_from_paper_to_fabric_so_all_my/)).
Thesis-perfect 2025 thread: "Paper ruins mob farms… I looked into cardboard… **apparently it kinda
sucks and is very buggy**"
([link](https://reddit.com/r/admincraft/comments/1jx3k5l/any_way_to_run_plugins_without_changing_minecraft/)).
Hybrid bridges have documented corruption/instability reputations
([Cardboard corrupting player data](https://reddit.com/r/admincraft/comments/qfyfll/cardboard_mod_corrupting_player_data/),
[EssentialsX: do-not-use-mohist](https://essentialsx.net/do-not-use-mohist.html)). Named-demand
density: CoreProtect (10+ threads, mostly pre-Ledger), claims/WorldGuard (10+), economy/shops
(7+), Multiverse (5+), NPCs/quests/crates (3+), minigame frameworks
([2025: auto-minigame plugins "don't seem to be working for 1.21.1"](https://reddit.com/r/admincraft/comments/1k13tb2/automatic_minigames/),
["minigame maker" wanted](https://reddit.com/r/admincraft/comments/1ikbv7f/minigames_how/)).
Mario Kart nostalgia: [367↑ server hugged to death](https://reddit.com/r/Minecraft/comments/1jn9np/reddit_already_broke_the_mario_kart_racing_server/),
[1,455↑ track build](https://reddit.com/r/Minecraft/comments/11ilty/any_mario_kart_players_out_there_built_this_as_a/),
[490↑](https://reddit.com/r/Minecraft/comments/miuvm9/made_a_mario_kart_track_with_my_friend_we_plan_to/).

---

## Section 3 — The ten port candidates

Ranked by (canon demand × Fabric absence × feasibility × fit with server-side skillset). Each:
what it ports, the vanilla-client baseline, the companion-client upgrade, competition, evidence,
and difficulty (S/M/L for a solo dev).

### 1. Turnkey minigame suite — "lobby in a jar"

- **Ports:** the dead 2017 stack (MinigamesLib, instancelabs BedWars/SkyWars ~1.7M combined) plus
  the lobby/queue/rotation infrastructure that only networks have today.
- **Vanilla baseline:** sgui lobby menus (HotbarGui game selector), scoreboard/bossbar state,
  chat+title announcements, map voting via BookGui/chat, runtime arenas via Fantasy-style
  dimensions or pre-generated template worlds, vanilla particles/sounds for juice. Ships with 3–5
  flagship games: Spleef, TNT Run, a BedWars-style objective game, a wave-defense (MobArena
  lineage), a party-games rotation.
- **Companion upgrade:** in-game HUD (timers, team status, kill feed), countdown overlays, custom
  sounds without RP, spectator free-cam, custom victory animations.
- **Competition:** Nucleoid (engine, zero packaging, invisible); Minimega (187k, LCE ports — not a
  lobby system); nothing else. On Bukkit, Screaming BedWars (503k) proves per-game demand.
- **Evidence:** [broken 1.21.1 minigame plugins](https://reddit.com/r/admincraft/comments/1k13tb2/automatic_minigames/),
  ["minigame maker" ask](https://reddit.com/r/admincraft/comments/1ikbv7f/minigames_how/),
  [minigame server setup asks](https://reddit.com/r/admincraft/comments/1k2ujkr/from_dream_to_reality_building_minigames_server/).
  Modrinth's minigame category is the weakest on the marketplace (leader 12.6M, no 26.x build).
- **Difficulty:** L — but Plasmid/Fantasy + 40+ open-source Nucleoid games are reference code.
- **Why it wins:** nobody sells the *product* (lobby+queue+rotation+cosmetics) on any mod loader;
  the category throne is empty and the Bukkit stack is rotting.

### 2. Kart racer — the Mario Kart lineage, revived

- **Ports:** Storm345's MarioKart (abandoned), MobRacers (18K, dead 2016), Hypixel TKR (demoted
  2017; [community still runs ranked in 2024](https://hypixel.net/threads/revert-the-hypixel-tournaments-suspension-preserve-competitive-play-for-the-community.5766293/)).
  **No living kart racer exists on any platform.**
- **Vanilla baseline:** minecart-physics karts (TaterCart-style server-side physics tweaks),
  display-entity kart models riding the minecart (Nylon for rigged models), item-box pickups via
  interaction entities, powerups as vanilla-item triggers (carrot-on-a-stick / F-swap / hotbar
  slots), lap/checkpoint logic server-side, sgui race lobby. Honest risk: vanilla vehicle feel —
  client prediction is tuned for vanilla minecarts, so the baseline must stay close to minecart
  physics.
- **Companion upgrade:** client-predicted kart movement (the hard wall, solved), custom kart
  models + drifting animations, position/lap HUD, drift-boost keybind, engine sounds.
- **Competition:** none alive. Create Aeronautics owns *physics building*, not racing games.
- **Evidence:** [367↑](https://reddit.com/r/Minecraft/comments/1jn9np/reddit_already_broke_the_mario_kart_racing_server/),
  [1,455↑](https://reddit.com/r/Minecraft/comments/11ilty/any_mario_kart_players_out_there_built_this_as_a/),
  [490↑](https://reddit.com/r/Minecraft/comments/miuvm9/made_a_mario_kart_track_with_my_friend_we_plan_to/),
  plus Turbo Kart Racers' persistent ranked community.
- **Difficulty:** L (vehicle feel is the research problem); M if scoped to minecart-physics-only.
- **Why it wins:** zero supply anywhere + proven nostalgia + YouTube-friendly. The dual-mode
  architecture is the moat: competitors must solve the same client-prediction problem.

### 3. Server economy suite — Vault + shops + auction house

- **Ports:** Vault (11.5M, stale-but-standard), ChestShop (6M), EconomyShopGUI (2.3M), AuctionHouse
  (766K, stale 2024).
- **Vanilla baseline:** economy API mod (balances, transactions, PlaceholderAPI interop) +
  MerchantGui/SimpleGui server shop + chest-GUI auction house with AnvilInputGui bidding + sign
  shops for the classic feel. All proven sgui patterns.
- **Companion upgrade:** richer shop/AH screens, price-history charts, sale notifications,
  custom currency item models (RP does this without a client mod too).
- **Competition:** Numismatic Overhaul (3.6M) and Impactor stalled at 1.21.1; Lightman's treats
  Fabric as second-class; all auction-house mods dead; Universal Shops (Patbox) is the closest —
  small. Modrinth's economy category leader has no 26.x build and isn't even an economy mod.
- **Evidence:** [Shopkeepers/QuickShop-style ask](https://reddit.com/r/admincraft/comments/1et5y2g/fabric_modpack_for_small_server_needing_help_with/),
  [chest shop ask](https://reddit.com/r/admincraft/comments/pj1qz0/up_to_date_chest_shop_mod/),
  [offline player-shop ask](https://reddit.com/r/admincraft/comments/1k02ade/looking_for_a_player_trade_plugin_but_the_item/),
  [FTB economy asks](https://reddit.com/r/feedthebeast/comments/v9skzu/mods_for_economy/).
- **Difficulty:** M.
- **Why it wins:** the economy *API* slot is vacant on current versions; being the API makes you
  the dependency (the Text Placeholder API playbook — 53.7M).

### 4. Cosmetic wardrobe — disguises, pets, trails, hats

- **Ports:** LibsDisguises (913K), GadgetsMenu (527K), UltraCosmetics (393K), PlayerParticles,
  MyPet/EchoPet — the Bukkit donation engine.
- **Vanilla baseline:** DisguiseLib-style packet disguises (fork maintained on GitHub), pets as
  display-entity/mob followers, particle trails via server-spawned vanilla particles, hats via
  PolymerHeadBlock/item displays, wardrobe sgui menu, per-player toggles.
- **Companion upgrade:** animated hat/pet models (Nylon), emotes, cosmetic preview rendering.
- **Competition:** none maintained server-side. Simple Hats (5.4M) is client+server content, not a
  server wardrobe; Cosmetica is client capes.
- **Evidence:** cosmetics drive Bukkit donations universally; the lobby-cosmetics pair alone is
  ~920K; zero server-side Fabric supply.
- **Difficulty:** M.
- **Why it wins:** pairs with #1 (lobby cosmetics are the monetization half of every minigame
  network) and stands alone for SMPs. Server owners *pay* for this on Bukkit; free + open wins
  mindshare.

### 5. NPC + quest engine

- **Ports:** Citizens (5.2M+, went premium 2025 — grievance timing), BetonQuest/Quests
  (540K combined), shopkeeper NPCs.
- **Vanilla baseline:** Taterzens-style fake-player NPCs (skins, poses, look-at), click/dialogue
  via chat + BookGui + AnvilInputGui branching quests, objective tracking via scoreboard,
  command/shop actions on interaction. Server-side quest scripting via JSON configs.
- **Companion upgrade:** dialogue-box UI instead of chat, quest-tracker HUD, NPC nameplate styling,
  cutscene camera.
- **Competition:** Taterzens stale at ≤1.21.6; Easy NPC requires the client mod; no server-side
  BetonQuest analog at all.
- **Evidence:** [NPC/quest/crates ask](https://reddit.com/r/admincraft/comments/133fpmu/does_anyone_know_of_similar_alternatives_to_these/),
  [MythicMobs alternatives](https://reddit.com/r/admincraft/comments/1c2dzea/mythicmobs_alternatives/),
  Citizens' 2025 paywall.
- **Difficulty:** M–L.
- **Why it wins:** NPCs are the connective tissue of lobbies, shops, quests, and RPG servers —
  and the incumbent just went paid.

### 6. Votes + crates — the monetization loop

- **Ports:** NuVotifier (stale 2021) + VotingPlugin + CrazyCrates/ExcellentCrates (~1.7M combined).
- **Vanilla baseline:** Votifier-protocol listener (RSA-decrypt vote payloads on a socket —
  pure server code), vote rewards/milestones, crate keys as Polymer items, crate opening as a
  display-entity + sgui animation with vanilla sounds, physical crate blocks via PolymerHeadBlock.
- **Companion upgrade:** full-screen crate-opening cinematic, key/item models, rarity effects.
- **Competition:** literally zero on Fabric — no vote listener, no crate mod.
- **Evidence:** NuVotifier+CrazyCrates is default infrastructure on nearly every public Bukkit
  server; the 34-plugin replacement list includes VotingPlugin
  ([link](https://reddit.com/r/admincraft/comments/18dvvd2/i_want_to_switch_from_paper_to_fabric_so_all_my/)).
- **Difficulty:** S–M.
- **Why it wins:** highest demand-to-zero-supply ratio on the gap map; small scope; natural
  attach to every other candidate here.

### 7. Towns & nations — Towny-style geopolitics

- **Ports:** Towny (several hundred K), Factions (5.4M dead; FactionsUUID paid).
- **Vanilla baseline:** town/nation creation, chunk claiming with upkeep costs (hooks candidate
  #3's economy), residents/mayors/ranks, war/raid windows, protection flags per claim, dynmap-style
  web map integration via squaremap API. All server logic; OPAC (19.5M) proves the claims market.
- **Companion upgrade:** claim-boundary visualization (WorldEditCUI pattern), town management
  screens, war HUD.
- **Competition:** one small Factions mod (31k, ≤26.1.1); nothing Towny-grade.
- **Evidence:** [SMP Earth-style claims ask](https://reddit.com/r/admincraft/comments/1foip77/any_land_claim_system_mod_like_smp_earth/),
  [Towny solution ask](https://reddit.com/r/feedthebeast/comments/1ig11tc/looking_for_towny_solution/),
  [Lands-plugin ask](https://reddit.com/r/feedthebeast/comments/1jkpbnz/mods_similar_to_lands_plugin/),
  [Factions/Power ask](https://reddit.com/r/feedthebeast/comments/1k6kcg9/factionspower_mod_1201_perferred/).
- **Difficulty:** L.
- **Why it wins:** Towny/Factions servers are a durable subculture with zero modern-modded home.

### 8. Multiverse-style world manager

- **Ports:** Multiverse-Core (9.3M).
- **Vanilla baseline:** runtime world create/delete/import over Fantasy, per-world gamemode and
  gamerules, world aliases, portal linking, spawn-per-world, inventory groups, sgui world browser.
- **Companion upgrade:** world-preview screens; little needed — this is an admin tool.
- **Competition:** Multiworld mod (67k) is command-only; Fantasy is library-only.
- **Evidence:** [Multiverse-equivalent asks](https://reddit.com/r/admincraft/comments/mboogl/are_there_any_multiverseequivalent_mods_for_fabric/)
  ([+](https://reddit.com/r/admincraft/comments/k1nx8b/multiverse_alternative_for_fabric/),
  [+](https://reddit.com/r/admincraft/comments/1djjov8/hi_does_anyone_know_how_to_make_a_hub_with/),
  [+](https://reddit.com/r/feedthebeast/comments/1iyi9il/need_a_mod_for_forge_1201_like_the_multiverse/)).
- **Difficulty:** M.
- **Why it wins:** prerequisite infrastructure for #1 and #7; a polished UX over Fantasy is a
  genuine product, not just a port.

### 9. WorldGuard-style admin flag regions

- **Ports:** WorldGuard (10.5M).
- **Vanilla baseline:** admin-defined cuboid/poly regions with build/pvp/mob-spawn/entry/exit
  flags, region priorities, global region, sgui flag editor, wand selection via vanilla item.
- **Companion upgrade:** region-boundary visualization (WorldEditCUI protocol — the oldest
  dual-mode pattern in existence).
- **Competition:** Leukocyte (9k, Nucleoid — minimal UX), Orbis (1.5k, brand new). Player
  self-serve claims are solved (OPAC); *admin* regions are not.
- **Evidence:** [WorldGuard-for-Fabric asks](https://reddit.com/r/admincraft/comments/n9xq4w/fabric_alternative_to_worldguard/)
  ([+](https://reddit.com/r/admincraft/comments/16q4o5t/worldguard_for_fabric/),
  [+](https://reddit.com/r/admincraft/comments/18ggjr8/switching_from_paper_to_fabric/)).
- **Difficulty:** M.
- **Why it wins:** the highest-download Bukkit protection plugin has no real Fabric analog; folds
  naturally into an admin suite with Ledger/BanHammer.

### 10. Holograms pro

- **Ports:** DecentHolograms (923K) / HolographicDisplays (3.1M stale).
- **Vanilla baseline:** packet text_display entities (HoloDisplays architecture), per-player
  pages, animations via interpolation, Placeholder API live values, sgui editor.
- **Companion upgrade:** marginal — display entities cover it.
- **Competition:** HoloDisplays (14k) has the right architecture but young; Patbox's Holograms
  dead and self-points to HoloDisplays.
- **Difficulty:** S.
- **Why it wins:** better as a *feature* of candidates #1/#4/#5 than a standalone product — but a
  standalone "Holograms+" is a fast, safe first release to build audience.

**Honorable mention — Ledger-plus:** CoreProtect asks persist on admincraft and Ledger's rollback
is "a big hit and miss." A reliability-focused rollback engine (or contributing fixes upstream) is
a real opening, but Ledger is healthy and the niche is largely served.

---

## Section 4 — The dual-mode architecture

### The stack (all verified current for 26.2)

| Layer | Library | What it gives vanilla clients |
|---|---|---|
| Content fakery | [Polymer](https://modrinth.com/mod/polymer) (3.4M) | Server-registered items/blocks/entities shown as vanilla stand-ins; `PolymerHeadBlock` custom-textured blocks with **no RP**; per-player variants |
| GUIs | [sgui](https://github.com/Patbox/sgui) | Chest (9×1–9×6), anvil text input, book, sign, merchant-trade, and **hotbar** GUIs |
| Visuals | polymer-virtual-entity, [HoloDisplays](https://modrinth.com/mod/holodisplays), [Nylon](https://github.com/Patbox/polymer/blob/dev/1.21/MODS.md) | Packet text/item/block displays with interpolation animation; rigged Animated-Java models server-side |
| Resource pack | polymer-autohost | Auto-builds + serves the merged pack; custom item models, GUI reskins, custom sounds (vanilla fallback without pack) |
| Dual-mode switch | polymer-networking | **Handshake**: per-player version check → real representation vs vanilla fallback ([docs](https://github.com/Patbox/polymer/blob/dev/26.2/docs/polymer-core/client-side.md)) |
| World instancing | [Plasmid](https://modrinth.com/mod/plasmid) + [Fantasy](https://github.com/NucleoidMC/fantasy) | Runtime template worlds, game spaces, Stimuli events |
| Interop | [Text Placeholder API](https://modrinth.com/mod/placeholder-api) (53.7M) | `%modid:type%` placeholders everywhere |
| NPCs/disguises | [Taterzens](https://modrinth.com/mod/taterzens) patterns, DisguiseLib fork | Fake players, packet entity-type swaps |

### Capability matrix

| Feature | Vanilla client only | + server resource pack | + client companion |
|---|---|---|---|
| Custom GUIs | ✅ chest/anvil/book/sign/merchant/hotbar (sgui) | ✅ + full-art reskin (custom-font title, ItemsAdder technique) | ✅ arbitrary screens/widgets |
| Custom item look | ⚠️ vanilla stand-in + name/lore/glint | ✅ custom models via `item_model` component | ✅ full (CIT, emissive, tooltips) |
| New blocks | ⚠️ vanilla stand-in / player-head skins | ✅ textured (polymer-blocks) | ✅ real blockstates + BERs |
| NPCs | ✅ fake players/mobs (Taterzens) | ✅ + custom skins | ✅ marginal gain |
| Disguises | ✅ packet swap (DisguiseLib) | ✅ same | ✅ same |
| Particles | ⚠️ vanilla types only | ⚠️ same (RP can't add types) | ✅ custom particle types |
| Sounds | ⚠️ vanilla via playsound packets | ✅ custom (PolymerSoundEvent + fallback) | ✅ + mixing/looping |
| Holograms | ✅ packet display entities | ✅ + font-glyph icons | ✅ little gain |
| Vehicles/mounts | ⚠️ vanilla physics tweaks only (jittery otherwise) | ⚠️ same | ✅ client-predicted custom vehicles |
| Keybinds | ❌ impossible (sneak/swap-slot hacks only) | ❌ impossible | ✅ keybind → custom packet |
| HUD | ⚠️ bossbar/actionbar/title/scoreboard/tablist | ✅ + font-glyph overlays | ✅ free-form HUD |
| Animations | ⚠️ display interpolation; Nylon rigs | ✅ RP models + interpolation | ✅ GeckoLib/emotes |

### Client detection

Standard primitive: **custom payload channels** — a vanilla client never registers yours, so
registration is the capability signal. Options: the cross-loader `c:register`/`c:version`
standard (jointly defined by Fabric/NeoForge/Paper/Sponge), Fabric API's
`ServerPlayNetworking.canSend` + `S2CPlayChannelEvents.REGISTER` (on 1.20.2+ wait for channel
registration, don't check at JOIN), or Polymer's own handshake (verified pattern:
`PolymerServerNetworking.getSupportedVersion(handler, PACKET_ID) > 0` → modded representation,
else vanilla fallback). Precedents: WorldEdit+WorldEditCUI (`worldedit:cui`), Distant Horizons
(server serves LODs only to DH clients), Simple Voice Chat, Emotecraft.

### Hard walls (vanilla-only) — and which candidate they touch

- New registry entries the client must know → Polymer fakery is the workaround (all candidates).
- Keybinds → companion only (#2 drift-boost, #4 emote wheel).
- Custom screens → companion only (#3 AH browser, #5 dialogue box).
- Custom particle types → companion only (cosmetic; #4, #6).
- Native-feeling custom vehicles → companion only (**#2's core risk**; baseline stays on minecart
  physics).

### Performance rules (from Polymer/Nucleoid docs + Mob Conduit experience)

- Event-driven over polling (Stimuli/Fabric events; never per-tick world scans).
- Packet/virtual entities over real entities for pure visuals (no ticking/AI/collision).
- Send packets only to tracking players (`PlayerLookup`), never global broadcasts.
- Async heavy setup (map template load, pack generation, DB writes) off the main thread.
- Known Polymer hot spots: block light re-sync, per-player blockstate variants, huge RP builds.

### Why the big plugins never ported (and won't)

Ecosystem gravity + monetization (CoreProtect donation keys, MythicMobs/Citizens premium, a decade
of user configs/scripts that a port would orphan). CoreProtect's Fabric-port request was closed
with "use Ledger" ([CoreProtect#395](https://github.com/PlayPro/CoreProtect/issues/395)). The
Fabric community reimplements rather than ports (Ledger, Taterzens, Fuji, PolymerPorts) — expect
to rewrite gameplay logic against Fabric/Polymer APIs, reusing data models and configs at most.

---

## Section 5 — Top picks

Section 3 ranks by raw demand; this list risk-adjusts (scope vs. certainty of the win), which is
why the economy suite outranks the kart racer here.

1. **Turnkey minigame suite** — biggest canon category × weakest Modrinth category × rotten Bukkit
   stack × Nucleoid engine sitting unmarked. The flagship play.
2. **Server economy suite** — the Vault/API slot is vacant on 26.x; being the economy API means
   becoming the dependency. Medium scope, certain category win.
3. **Kart racer** — zero living competition on *any* platform, massive nostalgia,
   YouTube-friendly; the dual-mode vehicle problem is the moat. Highest risk, highest ceiling.
4. **Cosmetic wardrobe** — the monetization half of every Bukkit server; trivial on Polymer; pairs
   with #1.
5. **NPC + quest engine** — Citizens' 2025 paywall is the timing; connective tissue for lobbies,
   shops, RPG.

Fast-follow bundle: **votes + crates** (#6) — small, zero supply, attaches to everything above.

Sequencing note: #10 (Holograms+) or #6 (votes+crates) are the best *first ships* — small enough
to build audience and harden the dual-mode stack before the flagship (#1) lands.

---

## Appendix — provenance and caveats

- Plugin counts: Modrinth API + Spiget (SpigotMC) + cfwidget (CurseForge), 2026-07-29; direct
  downloads (EngineHub, GeyserMC, Towny, Jenkins) excluded, so canonical totals run higher.
  Premium = Spigot purchases only (other storefronts unverifiable). mcMMO ">3M" is a vendor claim.
- Fabric gap map: Modrinth API v2, all queries 2026-07-29; counts exclude CurseForge (FTB suite
  and Lightman's skew low as a result).
- Reddit: PullPush archive; **no data after ~2025-05-18**; post-2023 scores unreliable, comment
  counts used instead. Newer sentiment could not be sampled.
- Technical claims verified against Polymer/sgui/Plasmid docs (dev/26.2 branches), Fabric
  networking docs, and minecraft.wiki protocol pages, 2026-07-29.
