# Mob Conduit

Stops hostile mobs spawning around it. Made out of vanilla blocks.

Server side only, so players don't install anything.

## How it stays server side

The mod doesn't register any new blocks, items, particles or sounds. Everything it uses is
already in vanilla, so there's no registry sync to fail and nothing for players to download.

That's also why the middle is an end crystal instead of a conduit block. A vanilla client draws
a conduit block in its inactive state no matter what the server says, so it would have looked
broken to everyone. An end crystal spins by itself, so you can tell whether it's on.

## Building one

Same frame as a vanilla conduit. Three rings, 42 blocks, netherite by default. Obsidian in the
middle with an end crystal on it.

No water. It works in open air.

16 frame blocks gives you a 64 block radius. A full 42 gives you 128.

## What it does

Natural hostile spawns stop inside the radius.

Spawners, trial spawners, spawn eggs, breeding and `/summon` all keep working, so a mob farm
inside the radius is fine. Passive and neutral mobs are ignored.

Anything hostile already inside when it switches on gets removed. A light turns on over the mob
and it comes apart in soul fire. This is spread over several ticks so a few hundred mobs won't
hitch the server. They get removed instead of killed, so there's no drops and no XP orbs sitting
around for five minutes afterwards. There's a config option if you want drops.

It also removes hostiles that wander in later, which you can turn off if you only want spawn
suppression.

While it's on it weeps obsidian tears off the frame and holds sculk souls around the crystal.

## Cost

A full netherite frame is about 1,512 ancient debris. That's on purpose. It's meant to be
something you build after you've already beaten the game.

If that's too much for your server, set `frame_block` to `minecraft:ancient_debris` in the
config. Same shape, much cheaper. Any vanilla block works.

## The crystal can be destroyed

Creepers will get it eventually and withers have no trouble with it. When the crystal goes the
conduit stops and spawning comes back.

Put a new crystal on the obsidian to start it again.

## Config

`config/mob-conduit.json`. Run `/mobconduit reload` to pick up changes without restarting, or
`/mobconduit set <key> <value>` to edit one key live (`/mobconduit get <key>` reads it back).

You can set the radius, the thresholds, the frame block, whether mobs drop loot, whether it
handles wanderers, and all the particle effects including which particle each one uses.

`/mobconduit status` puts a scoreboard on screen with a running count of how many spawns it's
blocking, and every suppressed spawn pings the action bar of players in range. `/mobconduit
visualize` draws each conduit's coverage sphere in particles for a few seconds. `/mobconduit
sweep` re-runs the erasure across every conduit in the dimension, and `/mobconduit build <pos>`
erects a full structure for testing.

## Requirements

Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API, Java 25. Server side only, clients need
nothing.
