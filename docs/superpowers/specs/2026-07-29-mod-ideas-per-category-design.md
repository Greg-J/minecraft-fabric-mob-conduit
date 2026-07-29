# Most-Popular-Mod-Per-Category — Market Research & Challenger Ideas

Date: 2026-07-29
Status: research complete, awaiting review

**Goal.** Generate Minecraft mod ideas with the explicit aim of holding the #1 popularity spot in
each marketplace category, under three constraint tiers:

- **Tier S — Server-side only:** no new registries, vanilla clients connect unmodified (the Mob
  Conduit constraint).
- **Tier M — Solo-buildable:** client-side mods and new content allowed, one-dev scope.
- **Tier X — Unrestricted:** team / total-conversion scale.

**Method.** Four parallel research streams, all sources cited inline:

1. **Modrinth API v2 leaderboards** (live, 2026-07-29): top 5 by all-time downloads for each of
   the 19 Modrinth categories, plus overall top 10. Queries:
   `GET https://api.modrinth.com/v2/search?limit=5&index=downloads&facets=[["categories:<CAT>"],["project_type:mod"]]`.
2. **CurseForge incumbent health**: all-time download estimates (CurseForge blocks anonymous
   scraping, so CF numbers are approximate, assembled from CF's own blog rankings and secondary
   sources), last-seen MC version, and a HEALTHY / STALE / ABANDONED / VERSION-LOCKED verdict per
   incumbent, verified largely via the public file-history mirror `files.xmdhs.com`.
3. **Reddit demand mining**: r/feedthebeast, r/minecraftsuggestions, r/admincraft via the PullPush
   API (covers through ~2025-05; vote counts for 2025 may read slightly low) plus web search for
   later threads. Two outputs: unmet demand and incumbent grievances.
4. **Mojang feedback site + ecosystem trends**: Mojang's official rejected-suggestions list
   (feedback.minecraft.net blocks logins since 2023 and scraping; all-time vote totals are
   UNVERIFIED), modpack composition (ATM9→ATM10), and breakout-mod trajectories.

**How to read "the bar."** Modrinth download counts are exact (API). CurseForge counts are
approximate. Many category #1s are client-QoL mods wearing the category tag (VeinMiner leads three
categories; Iris "leads" decoration). For content mods the meaningful bar is the **top content
mod** in the category, which is usually far lower than the nominal #1. Both bars are given.

---

## Section 1 — The bar per category

### Overall Modrinth top 10 (global context)

| # | Mod | Downloads | What it is |
|---|---|---|---|
| 1 | Fabric API | 218,114,806 | Fabric standard library |
| 2 | Sodium | 195,391,958 | Render engine replacement |
| 3 | Iris Shaders | 152,359,588 | Shader loader |
| 4 | Cloth Config API | 146,852,356 | Config library |
| 5 | Entity Culling | 142,507,344 | Occlusion culling |
| 6 | FerriteCore | 133,090,982 | Memory optimization |
| 7 | Mod Menu | 126,149,575 | In-game mod list |
| 8 | Lithium | 112,760,637 | Tick/logic optimization |
| 9 | ImmediatelyFast | 108,562,916 | Immediate-mode rendering |
| 10 | YetAnotherConfigLib | 106,488,811 | Config library |

**Zero content mods in the global top 10.** The biggest numbers are driven by being a dependency of
modpacks, not by gameplay appeal.

### Per-category leaders

| Category | Nominal #1 (Modrinth dl) | Top *content* mod in top 5 | CF-side incumbent health |
|---|---|---|---|
| adventure | Xaero's Minimap — 97.1M | none (all client QoL) | Twilight Forest ~150M CF, last release 2025-04, **STALE**; Aether healthy-ish; Alex's Caves 1.20.1-only **STALE** |
| cursed | Sodium Extra — 85.0M | n/a (joke tag) | — |
| decoration | Iris — 152.4M | none in top 5 | Supplementaries 23.7M Modrinth **HEALTHY**; MrCrayfish Refurbished 43M CF on 26.1 **HEALTHY**; Chipped ~50M CF **STALE** |
| economy | CTO Village — 9.6M, **no 26.x build** | none (village worldgen wearing the tag); VillagersPlus #3 abandoned since 2023-12 | no true currency/shop incumbent anywhere |
| equipment | VeinMiner — 66.6M (tag spam) | Curios (API) 27.6M; TaCZ 25.5M Forge-only | TaCZ ~28.4M+ CF **stranded on 1.20.1**; Tinkers' ~120M CF **VERSION-LOCKED 1.20.1** |
| food | AppleSkin — 78.6M (HUD) | Farmer's Delight 20.5M (+13.6M Fabric port) | FD ~100M CF **HEALTHY**; Pam's suite **STALE legacy** |
| game-mechanics | VeinMiner — 66.6M (tag spam) | Cobblemon 31.0M | — |
| library | Fabric API — 218.1M | — | **locked** |
| magic | Enchantment Descriptions — 33.3M (tooltip mod) | Alex's Caves 9.6M, **stale since 2024-10** | Ars Nouveau ~70M CF **HEALTHY**; Botania ~100M & Blood Magic ~90M **VERSION-LOCKED 1.20.1**; Astral Sorcery **ABANDONED 1.16.5** |
| management | YACL — 106.5M (config lib) | Text Placeholder API 53.7M (server-side lib) | CoreProtect is Paper-only; Ledger rollback "hit and miss" |
| minigame | Exposure — 12.6M, **no 26.x build** | none; #2 is a Cobblemon addon at 5.1M, #3 2.3M | **category cliff — no actual minigame mod in the top 5** |
| mobs | EMF — 84.5M (client cosmetic) | Cobblemon 31.0M (last release 2026-01) | Alex's Mobs ~100M CF, last update 2024-09, **STALE**, Forge-only; Pixelmon version-locked + platform-exiled |
| optimization | Sodium — 195.4M | — | **locked** (entire top 5 > 112M) |
| social | Simple Voice Chat — 59.7M, healthy | — | Essential healthy but hated-adjacent (closed-source, paid cosmetics) |
| storage | Mouse Tweaks — 50.9M (client) | none — no storage-system mod in top 5 | RS/Sophisticated/AE2/Drawers all ~150M CF, mostly healthy; Drawers drifting stale |
| technology | VeinMiner — 66.6M (tag spam) | Create 22.3M (#4) | Mekanism ~200M CF healthy; Thermal ~100M CF **dead since 2024-05**; Immersive Eng. chronic slow-porter; Create 26.x port in progress |
| transportation | Xaero's Minimap — 97.1M (tag spam) | Carry On 22.6M; Waystones 22.0M | Immersive Railroading **ABANDONED 1.12.2/1.16.5**; MrCrayfish Vehicle **STALE**, unofficial ports circulating |
| utility | FerriteCore — 133.1M | — | **locked** |
| worldgen | TerraBlender — 34.9M (library) | Biomes O' Plenty 30.1M | BoP ~190M CF healthy; Terralith ~40M CF **stale since 2025-01**; BYG abandoned → BWG |

Stale/mod-version-lagging leaders worth noting: `exposure` (minigame #1) supports only
1.19.2/1.20.1/1.21.1 — zero 26.x builds. `ct-overhaul-village` (economy #1) tops out at 1.21.11.
`villagersplus` (economy #3) dead 2.5 years. `alexs-caves` (magic #5) 21 months stale.
`eating-animation` (food #3) ~19 months stale.

---

## Section 2 — The three lists

Ideas marked **skip** mean no credible #1 play exists at that tier; forcing one would waste the
slot. Each idea lists its demand evidence.

### adventure

**Bar:** nominal 97.1M (Xaero, client). Content bar: Twilight Forest ~150M CF, 15 months quiet.

- **Tier S — Server-wide adventure events.** Scheduled server events (invasions, expeditions,
  boss nights) run entirely with vanilla mobs, scoreboards, boss bars and chat. Targets the
  player-retention demand cluster that server admins raise constantly with no tooling answer
  ([retention plugins/datapacks](https://www.reddit.com/r/admincraft/comments/13kt410/),
  [SMP retention](https://www.reddit.com/r/admincraft/comments/141ow1s/),
  [55↑ inactive-player pruning](https://www.reddit.com/r/admincraft/comments/1er3c9h/)).
- **Tier M — Dungeon/structure pack in the Dungeons-and-Taverns mold** (19.3M Modrinth), themed on
  **mob-vote losers and scrapped content** — the #2 all-time r/minecraftsuggestions post
  ([6,123↑](https://www.reddit.com/r/minecraftsuggestions/comments/jeqpf0/)). Datapack-mod hybrid
  is genuinely solo-scope.
- **Tier X — A new flagship dimension for 26.x.** The strongest single opening in modding:
  Twilight Forest ~150M CF, last release 2025-04, nothing for 26.x
  ([empty 26.1 listing](https://files.xmdhs.com/curseforge/history?id=227639&ver=26.1)); Aether
  carries perpetual "is it dead?" anxiety
  ([1,013↑](https://www.reddit.com/r/feedthebeast/comments/1fnqai1/),
  [2,098↑](https://www.reddit.com/r/feedthebeast/comments/1dz2heu/)); Alex's Caves is 1.20.1-only
  with community ports filling the vacuum. An unofficial Twilight Forest port already appeared on
  Modrinth — demand smoke.

### cursed

Joke tag; leader is Sodium Extra (85.0M). **Skip all tiers.**

### decoration

**Bar:** nominal 152.4M (Iris — a shader loader). Content bar: Supplementaries 23.7M Modrinth /
MrCrayfish Refurbished 43M CF, both healthy.

- **Tier S — Armor-stand sculpting studio + heads database.** Armor stands, item frames and player
  heads are vanilla entities/items that render on vanilla clients; posing/editing them server-side
  is a decade-proven Bukkit niche with no dominant modded equivalent.
- **Tier M — Functional furniture.** The single most-upvoted furniture thread in the dataset:
  [4,846↑, top comment 1,947↑](https://www.reddit.com/r/feedthebeast/comments/1e0o1i5/) — "what's
  the point of a king-size bed I can't sleep in / a fridge that can't store food." Mojang rejects
  furniture outright
  ([rejection list](https://feedback.minecraft.net/hc/en-us/articles/360005029872--Archived-Previously-Considered-Suggestions)),
  so vanilla will never compete. Handcrafted-quality art + actual function (fridges store, chairs
  sit, lamps light).
- **Tier X — Definitive furniture/decor platform.** Functional + Create-integrated + shader-era
  art, multi-loader, real documentation. Contested: Supplementaries and Refurbished are healthy —
  win on function and docs, not on block count.

### economy

**Bar:** 9.6M — weakest category leader on Modrinth, and it has no 26.x build. #3 is abandoned.
There is no true currency/shop mod in the top 5 at all (#5 is a *Cobblemon addon*).

- **Tier S — Item-barter auction house + chest-GUI shops.** Chest GUIs render on vanilla clients,
  so a full auction house is server-side-buildable today. Direct recurring admincraft demand:
  [item-trading chestshops](https://www.reddit.com/r/admincraft/comments/1kn9oh0/),
  [auction house trading items not money](https://www.reddit.com/r/admincraft/comments/1imfjuu/),
  [supply/demand economy](https://www.reddit.com/r/admincraft/comments/1hpbjbg/). Easiest #1 in
  the tier after minigame.
- **Tier M — A real currency mod.** Coins, wallets, player shops, admin shops. Nobody owns
  currency in this category; the village-overhaul leader doesn't even serve the ask.
- **Tier X — Server-economy platform:** currency + shops + auctions + jobs + web dashboard — the
  Vault/EssentialsX economy stack, rebuilt for modded servers.

### equipment

**Bar:** nominal 66.6M (VeinMiner tag spam — it also leads game-mechanics and technology). Content
bar: TaCZ 25.5M Modrinth, Forge-only.

- **Tier S — Gear loadout/kit system + vanilla-attribute rebalancing.** Server-side item attribute
  and loadout management for arena/RPG/SMP servers. Modest demand; the weakest entry in this
  tier — included for completeness.
- **Tier M — A modern gun mod for Fabric/NeoForge 26.x.** The cleanest demand-without-supply gap
  in the dataset: TaCZ has 28.4M+ CF downloads and is stranded on Forge 1.20.1
  ([platform limits](https://gdlauncher.com/mods/curseforge/timeless-and-classics-zero));
  MrCrayfish's Gun Mod adds 24M+ more, also old-version; guns are a
  ["solid no"](https://feedback.minecraft.net/hc/en-us/articles/360005029872--Archived-Previously-Considered-Suggestions)
  from Mojang, so vanilla will never compete. A quality gun mod on current Fabric/NeoForge has a
  pre-built audience measured in tens of millions.
- **Tier X — Tinkers' Construct successor for 26.x.** TiC has ~120M CF downloads, is actively
  maintained, and has *never* ported past 1.20.1
  ([file history](https://files.xmdhs.com/curseforge/history?id=74072&ver=1.20.1)). Modular
  tool/weapon crafting has no home on current Minecraft. Team-scale scope (smeltery, modifiers,
  materials data), but the throne has been empty for five years.

### food

**Bar:** nominal 78.6M (AppleSkin, a HUD mod). Content bar: Farmer's Delight ~100M CF — healthy.

- **Tier S — Harvest-festival / food-event system.** Seasonal SMP food events with vanilla items
  (community feasts, harvest contests). Ties into the retention tooling admins ask for. Weak-ish;
  included for completeness.
- **Tier M — Cooking-for-effects.** Meals as the buff system: a proper potion-meals progression
  that differentiates from FD's cozy-farming lane rather than cloning it. FD compatibility as a
  feature, not a fight.
- **Tier X — skip.** FD's ecosystem (~100M CF, actively updated, huge addon scene) is not
  dethronable at any sane budget.

### game-mechanics

**Bar:** nominal 66.6M (VeinMiner tag spam). Content bar: Cobblemon 31.0M.

- **Tier S — Curated server tweak-pack.** Treecapitator, graves, multiplayer-sleep, granular death
  rules — death-penalty config is asked from *both* directions
  ([keep-inventory alternatives](https://www.reddit.com/r/feedthebeast/comments/1kd5slp/),
  [harsher death](https://www.reddit.com/r/feedthebeast/comments/16qwv2d/)). VeinMiner itself
  (66.6M, ships Bukkit/datapack variants) proves vanilla-compatible tweak mods are among the
  most-installed things in existence.
- **Tier M — ProjectE-style transmutation (EMC) for 26.x.** Recurring revival asks:
  [ProjectE alternative for 1.21.1](https://www.reddit.com/r/feedthebeast/comments/1kh33bg/),
  [Philosopher's Stone alternatives](https://www.reddit.com/r/feedthebeast/comments/1jahszl/),
  and EE named in the [dead-mods revival thread](https://www.reddit.com/r/feedthebeast/comments/1ikmbq5/).
  Nothing fills the transmutation niche on modern versions.
- **Tier X — The modern Quark.** Quark is a top-10 all-time CF mod (~150M+), frozen on 1.20.1 with
  no 1.21/26.x port at all
  ([empty 1.21.1 listing](https://files.xmdhs.com/curseforge/history?id=243121&ver=1.21.1)).
  Vertical slabs — its signature — are Mojang-rejected, so the demand never dies
  ([rejection list](https://feedback.minecraft.net/hc/en-us/articles/360005029872--Archived-Previously-Considered-Suggestions)).
  Supplementaries took one slice; the full "collection of small things" surface is unclaimed.

### library

**Locked.** Fabric API 218.1M; the top 5 are all infrastructure with pack-dependency gravity.
**Skip all tiers.**

### magic

**Bar:** nominal 33.3M — a tooltip mod. The top true magic-content entry on Modrinth is Alex's
Caves at #5, 21 months stale. CF side: Ars Nouveau healthy (~70M), Botania and Blood Magic
version-locked on 1.20.1.

- **Tier S — Particle-driven spell system on vanilla triggers.** Spells cast with
  carrot-on-a-stick detection, XP as mana, vanilla particles for effects. Bukkit magic plugins
  proved this viable for years; zero registries needed.
- **Tier M — A focused modern magic mod with a real in-game guide.** One strong spell system,
  ship-day 26.x support, and documentation as the differentiator — docs abandonment is the loudest
  meta-grievance in the dataset
  ([916↑](https://www.reddit.com/r/feedthebeast/comments/1jsp0y9/),
  [819↑, top comment 1,187↑](https://www.reddit.com/r/feedthebeast/comments/1dzfkjb/)). Modrinth's
  magic throne is literally a tooltip mod; Botania and Blood Magic never reached 1.21+.
- **Tier X — Thaumcraft-grade revival.** Pent-up demand is enormous
  ([TC7 trailer thread, 2,454↑/442c](https://www.reddit.com/r/feedthebeast/comments/1kq9h7z/)) —
  but CoFH owns the brand and is building TC7 (no public builds yet). Contested; enter only with a
  distinct identity, not a TC clone.

### management

**Bar:** nominal 106.5M (YACL, a config library). The server-side text library Text Placeholder
API sits at 53.7M — proof that server-side-only mods print enormous download numbers.

- **Tier S — CoreProtect-for-modded: block logging + rollback for Fabric/NeoForge.** The single
  most repeated admincraft ask in the dataset:
  [CoreProtect alternative](https://www.reddit.com/r/admincraft/comments/1hb86rc/),
  [block-logging mods for Forge](https://www.reddit.com/r/admincraft/comments/1hjvba6/),
  [Forge 1.20.1 block logger](https://www.reddit.com/r/admincraft/comments/1kmlu8m/),
  [admin tools & anti-grief for Forge](https://www.reddit.com/r/admincraft/comments/1k9c64r/),
  [grief-prevention mod](https://www.reddit.com/r/admincraft/comments/1bqxiqx/),
  [anti-grief 1.19.2](https://www.reddit.com/r/admincraft/comments/133b1n0/). The incumbent
  (Ledger) is called ["a big hit and miss"](https://www.reddit.com/r/admincraft/comments/1k77vxa/)
  on rollback. **Strongest server-side opening in this document.**
- **Tier M — Free, open, trusted anti-cheat for modded servers.** Recurring asks
  ([Fabric anti-cheat](https://www.reddit.com/r/admincraft/comments/1d5rhv7/),
  [prevent hacking on Fabric](https://www.reddit.com/r/admincraft/comments/1k66q3j/)); commercial
  incumbents are distrusted ([Vulcan vulnerability](https://www.reddit.com/r/admincraft/comments/19cxtmb/),
  [Spartan telemetry](https://www.reddit.com/r/admincraft/comments/1e4dbp8/)) and paid.
- **Tier X — EssentialsX-for-modded.** Logging + rollback + permissions + ranks + moderation +
  Discord server management in one trusted bundle. Hybrid plugin bridges are
  ["very unstable"](https://www.reddit.com/r/admincraft/comments/1fk2cbz/); nobody offers the
  plugin-grade admin stack modded servers keep asking for.

### minigame

**Bar:** 12.6M — and the leader (Exposure, a camera mod) has **no 26.x build**. #2 is 5.1M. There
is not one actual minigame mod in the top 5. Weakest category in the marketplace.

- **Tier S — Server-side minigame framework + flagship games.** Spleef, TNT-run, bedwars-style
  games on vanilla blocks, chest-GUI lobbies, scoreboard state. Bukkit proved this model for a
  decade; the modded category is empty. **Easiest #1 in the dataset.**
- **Tier M — A single polished party game** (cards/board-game style) with server play. Exposure's
  12.6M shows a one-mechanic social toy can top this category; a real game beats it.
- **Tier X — Hypixel-grade minigame network framework** for modded servers: arenas, queues,
  cosmetics, stats, map tooling.

### mobs

**Bar:** nominal 84.5M (EMF, a client cosmetic renderer). Content bar: Cobblemon 31.0M.

- **Tier S — Mob-behavior suite.** Smarter bees
  ([698↑](https://www.reddit.com/r/feedthebeast/comments/1jv72c8/)), "peaceful days"
  inverse-blood-moon
  ([36↑](https://www.reddit.com/r/feedthebeast/comments/1kpk2ns/)), villager hardiness
  ([thread](https://www.reddit.com/r/feedthebeast/comments/1korc1m/)) — pure AI/logic changes,
  zero registries, vanilla clients see only behavior. Mob Conduit's natural siblings; same
  architecture, same audience.
- **Tier M — Alex's-Mobs-grade creature mod for Fabric/26.x.** Alex's Mobs has ~100M CF downloads,
  no update since 2024-09
  ([version history](https://alexs-mobs-unofficial.fandom.com/wiki/Version_1.22.9)), is Forge-only,
  and community continuations already circulate. A Fabric creature mod is explicitly asked for
  ([thread](https://www.reddit.com/r/feedthebeast/comments/1kdouzx/)). Real-world animals are
  Mojang-rejected yet proven (sharks/crocodiles on the rejection list; Alex's Mobs built a 100M
  franchise on them).
- **Tier X — The Cobblemon playbook applied to vanilla-style creatures.** The definitive
  mob-vote-losers + scrapped-content mod (6,123↑ all-time suggestion), or a Mo'-Creatures-scale
  modern animal platform. Cobblemon proved the "modern, open-source, pack-friendly replacement for
  a stale classic" formula generalizes.

### optimization

**Locked.** Sodium 195.4M; entire top 5 above 112M; all aggressively maintained on the 26.x cycle.
**Skip all tiers.**

### social

**Bar:** 59.7M Simple Voice Chat — healthy, 12 platforms. Locked at the top, but text-side and
grievance-side openings exist.

- **Tier S — Text-side social suite.** Mail, chat games/trivia, group channels, proximity text.
  SVC owns voice; nobody owns text-side SMP social.
- **Tier M — Lightweight open-source friends/party mod** with free cosmetics and no hosting
  infrastructure. Essential's grievances (closed-source, paid cosmetics, bloat,
  [sync failures](https://www.reddit.com/r/feedthebeast/comments/1kpdc9i/),
  [notification spam](https://www.reddit.com/r/feedthebeast/comments/1k9mfbc/)) are exploitable
  without rebuilding its hosting stack.
- **Tier X — Open-source Essential killer:** friends + free world hosting + cosmetics, no upsell.
  Requires real hosting infra — team scale and ongoing costs.

### storage

**Bar:** nominal 50.9M (Mouse Tweaks, client UX). No storage-*system* mod in the Modrinth top 5;
the CF incumbents (RS, Sophisticated, AE2, Drawers) sit ~150M and are mostly healthy.

- **Tier S — Virtual backpacks / ender-chest-plus via chest GUI.** Plugin-style `/ec`, `/back`.
  Works, but client-side inventory mods outcompete on UX. Weakest entry in the tier.
- **Tier M — Approachable *and* performant storage.** The gap is named verbatim in the 172-comment
  storage shootout ([46↑/172c](https://www.reddit.com/r/feedthebeast/comments/1kez9bi/)): RS and
  Tom's and SSN are all called "laggy," AE2 wins on performance and loses on approachability
  (channels hated even by fans). A storage mod that is easy *and* TPS-friendly, with real docs,
  threads the needle. Brand moats make this contested.
- **Tier X — Next-gen storage + logistics:** AE2-grade performance, RS-grade simplicity, modern
  UX. Dethroning ~150M CF incumbents is a team-years effort.

### technology

**Bar:** nominal 66.6M (VeinMiner tag spam — it beats Create 3:1 via tagging). Content bar: Create
22.3M Modrinth; Mekanism ~200M CF healthy; Thermal series ~100M CF dead since 2024-05.

- **Tier S — Vanilla-block multiblock machines.** The Mob Conduit pattern generalized: player-built
  structures of vanilla blocks that grant automation (auto-smelter, tree farm, sorter) with no new
  registries. Distinctive — nobody else can offer "tech mod your vanilla friends can see."
- **Tier M — The Oritech play.** A mid-tier modern tech mod with day-one 26.x ports. Oritech went
  from unknown to ATM10 flagship within ~2 years
  ([ATM9→ATM10 additions](https://all-themods.com/vs-9/)); the Thermal series' death left classic
  mid-tier automation unclaimed, and big packs fast-track timely newcomers.
- **Tier X — skip / contested.** Create owns aesthetic automation and Create Aeronautics
  (5.6M downloads in under 6 months since Feb 2026) has all the momentum; Mekanism owns industry.
  No open throne.

### transportation

**Bar:** nominal 97.1M (Xaero's, tag spam). Content bar: Carry On 22.6M. Every dedicated
vehicle/train incumbent is dead or dormant.

- **Tier S — Rail/portal network manager.** Station booking, boosted-rail corridors, portal
  network mapping — orchestrating vanilla carts and portals server-side. Modest demand.
- **Tier M — Simple modern vehicles for 26.x.** Immersive Railroading is abandoned at 1.12.2/1.16.5;
  MrCrayfish's Vehicle Mod is stale with
  [GitHub issues full of "update request"](https://github.com/MrCrayfish/MrCrayfishVehicleMod/issues)
  and unofficial 1.21.1 ports circulating — the strongest demand-smoke pattern there is.
- **Tier X — contested.** Create Aeronautics shipped Feb 2026 and did 5.6M downloads in under 6
  months; player-built physics vehicles now have a flagship with momentum. Build atop the
  Valkyrien Skies ecosystem or stay out.

### utility

**Locked** (FerriteCore 133.1M; top-5 floor ~89M, all client/meta). The one adjacent opening — the
modern Quark — is listed under game-mechanics, Tier X. **Skip.**

### worldgen

**Bar:** nominal 34.9M (TerraBlender, a library — being the dependency of many worldgen mods is
the winning position here). Content bar: BoP 30.1M healthy; Terralith ~40M CF stale since 2025-01.

- **Tier S — Continents-style worldgen as a mod+datapack hybrid.** Worldgen is server-side by
  nature (vanilla clients generate nothing), so this is fully Tier-S-compatible. Direct ask:
  ["Should continental world generation be brought back?"](https://www.reddit.com/r/minecraftsuggestions/comments/1k621dy/)
  — 3,085↑/294c in 2025. Tectonic/Continents exist but are niche; no dominant mod markets itself
  on this exact fix.
- **Tier M — Continent-scale biome set.** The continents demand, plus BoP-grade biome quality on
  top — BoP is healthy but
  [slow-ported and conflict-prone](https://www.reddit.com/r/feedthebeast/comments/1eqjue5/)
  (1,646↑), and Terralith has been quiet for 18 months.
- **Tier X — Terralith-scale overworld overhaul for 26.x.** Overworld + caves + continents at
  datapack+mod scale, positioned as the maintained successor while the incumbent sleeps.

---

## Section 3 — Winnability analysis

**Open thrones (no serious incumbent):**
1. **minigame** — leader has no 26.x build; #2 at 5.1M; no minigame mods in the top 5 at all.
2. **economy** — leader at 9.6M with no 26.x build; no currency/shop mod anywhere in sight.
3. **magic (Modrinth)** — the #1 is a tooltip mod; content incumbents are stale or version-locked.
4. **management (server-side tooling)** — CoreProtect is Paper-only; the one modded incumbent is
   "hit and miss."
5. **adventure (dimension slot)** — Twilight Forest stale 15 months; Aether anxious; Alex's Caves
   vaporware.

**Fake bars (tag-spam leaders; real content bar ≤ ~31M):** equipment, game-mechanics, technology,
mobs, worldgen, transportation, decoration, storage, adventure. In these categories a strong
content mod competes with the *content* bar, not the nominal #1.

**Locked (do not enter):** optimization (Sodium 195M), library (Fabric API 218M), utility
(FerriteCore 133M), social (SVC healthy), food (FD healthy at Tier X), cursed (joke tag).

**The staleness pattern.** Incumbent weakness clusters on two boundaries: the 1.20.1→1.21.1
version wall (Quark, TiC, Botania, Blood Magic, Alex's Mobs, Thermal, Twilight Forest) and the
Forge→NeoForge split (Pixelmon, OptiFine, TaCZ, all of the above). Where authors are absent,
unofficial community ports are already circulating (Alex's Mobs/Caves, Vehicle Mod, Twilight
Forest) — the strongest possible unmet-demand signal. Fast, reliable 26.x ports are simultaneously
the challenger's cheapest advantage and the incumbent's most common failure mode.

**Cross-cutting strategy (applies to every idea above):**
- **Ship real docs + an in-game guide.** The loudest grievance in the dataset
  ([916↑](https://www.reddit.com/r/feedthebeast/comments/1jsp0y9/);
  [819↑, top comments 1,187↑/633↑](https://www.reddit.com/r/feedthebeast/comments/1dzfkjb/)):
  outdated wikis and "join our Discord" as documentation.
- **Day-one multi-loader** (Fabric + NeoForge minimum). Nearly every category leader ships Fa+Fo+NF;
  single-loader leaders are the exception.
- **Hand-crafted quality is marketable.** Anti-AI-slop sentiment runs hot
  ([863↑](https://www.reddit.com/r/feedthebeast/comments/1j7ywdp/)); "not MCreator" is a quiet
  quality signal.
- **Publish on both Modrinth and CurseForge.** Sodium/Iris leaving CF in 2023 proves platform
  exile costs reach; Modrinth-only and CF-only both leave downloads on the table.

## Section 4 — Top picks

Ranked by (demand evidence × incumbent weakness × fit with existing server-side skillset):

1. **CoreProtect-for-modded** (management, Tier S) — the #1 admincraft ask, a flaky incumbent,
   and exactly the Mob Conduit architecture (server-side, event-driven, no registries).
2. **Server-side minigame framework** (minigame, Tier S) — weakest category in the marketplace,
   Bukkit-proven model, vanilla-client compatible.
3. **Fabric/NeoForge gun mod for 26.x** (equipment, Tier M) — 28M+ downloads stranded on Forge
   1.20.1, Mojang-rejected so vanilla never competes.
4. **Flagship dimension for 26.x** (adventure, Tier X) — biggest unattended throne in modding
   (~150M CF incumbent, 15 months quiet). Team-scale.
5. **ProjectE-style transmutation** (game-mechanics, Tier M) or **modern Quark** (Tier X) —
   depending on appetite for scope; both revive ~100M+ demand frozen on 1.20.1.

Runner-up: **item-barter auction house** (economy, Tier S) — small scope, certain category win,
natural companion to picks 1–2 for the same server-owner audience.

---

## Appendix — research provenance

- Modrinth API v2, 20 search queries, all succeeded 2026-07-29 (19 categories + overall). Numbers
  in Section 1 are verbatim API values.
- CurseForge figures are estimates from CF blog rankings and secondary sources; last-update and
  MC-version data verified via `files.xmdhs.com` file-history mirror and Modrinth version pages.
- Reddit data via PullPush API (coverage through ~2025-05; 2025 vote counts may read low) plus web
  search for 2025–2026 threads. Sub-5-score threads cited only where the theme recurs.
- feedback.minecraft.net blocks automated access and logins (broken since 2023, bug WEB-6665);
  rejection-list content fetched in full, all-time vote totals UNVERIFIED.
- Loader-split context: NeoForge declared 26.1 the stable modding version
  ([neoforged.net, Mar 2026](https://neoforged.net/news/26.1release/)); big-pack gravity is
  NeoForge/1.21.x→26.x, while Fabric still owns client-side/casual players.
