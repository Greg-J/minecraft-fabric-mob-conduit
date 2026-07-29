# Mob Conduit

Spawn-proofing a big base is tedious. You either carpet the place in light blocks, slab every
surface for a hundred blocks, or give up and fight zombies in your own storage room forever.

So I built this. It's one structure that shuts hostile spawning off in a radius around it, and
it's made entirely out of vanilla blocks.

## It's server-side only

Your players install nothing. They join with a stock client and it just works.

I mean that literally — the mod adds no blocks, no items, no recipes, no particles, no sounds.
Nothing gets registered, so there's no registry sync to fail. Everything you see is a vanilla
block, a vanilla particle, or a vanilla sound. Drop the jar on the server and you're done.

## Building one

If you've built a conduit, you already know the shape. Three rings, 42 blocks, same geometry.

- **Frame:** netherite blocks by default, in the vanilla conduit pattern
- **Centre:** obsidian with an end crystal on top

No water. It works in open air.

The crystal is the on/off indicator. It spins when it's running. That's not a coincidence, it's
the whole reason I used a crystal instead of a conduit block — a vanilla client always draws a
conduit block in its inactive state, so it would have looked broken to everyone but me.

More frame blocks, bigger radius. 16 gets you 64 blocks, a full 42 gets you 128.

## What it actually does

Hostile mobs stop spawning naturally inside the sphere. That's it, that's the mod.

Things that keep working, on purpose:

- Mob spawners and trial spawners
- Spawn eggs
- Breeding
- `/summon`

So your mob farm inside the radius is fine. Passive and neutral mobs are never touched.

Anything hostile already inside when you switch it on gets erased. A light blinks on over its
head, it comes apart in soul fire, and the flames climb twenty blocks. It's staged across ticks
so a couple hundred mobs won't hitch your server. And it's a removal, not a kill — no drops, no
XP, no five minutes of item entities lying around. You can turn drops on if you'd rather.

By default it also handles anything that wanders in later. You can turn that off if you want
pure spawn suppression.

While it's running it weeps obsidian tears off the frame and holds a haze of sculk souls around
the crystal, so you can tell at a glance it's on.

## It's expensive and that's the point

At the netherite default a full frame runs you about 1,512 ancient debris. This is meant to be
something you build once, late, after you've already won.

If that's too much for your server, set `frame_block` to `minecraft:ancient_debris` in the
config. Same look, tiny fraction of the cost. Or point it at any vanilla block you like.

## The crystal is destructible

Blow it up and the conduit shuts off. Creepers will eventually find it. Withers absolutely will.

I left that in deliberately. It's a real structure with a real weak point, not a magic bubble.
Put a new crystal on the obsidian and it comes back.

## Config

Everything's in `config/mob-conduit.json`, and `/mobconduit reload` re-reads it without a
restart. Radius, thresholds, frame block, drops, forcefield, and every particle effect including
which particle each one uses.

There's also `/mobconduit status`, which puts a live scoreboard on screen showing how many spawn
attempts it's blocking in real time. Handy for convincing yourself it's working.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API
- Java 25
- Server side only. Clients need nothing.
