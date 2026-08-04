#!/usr/bin/env python3
"""End-to-end state checks for Mob Conduit, driven over RCON.

Asserts *state*, not gameplay: conduit registration, persistence across a restart, the base
block swap, hologram dedup, and config round-trips. It deliberately says nothing about spawn
suppression rates — natural spawning is player-driven and barely runs with nobody online, so a
headless verdict on it would be misleading. Greg runs those tests.

Two phases, because the most valuable check is a restart cycle and this script never launches or
waits on a server:

    python3 tools/rcon-battery.py phase1 --port 25575     # build, assert, then stop the server
    <restart the server>
    python3 tools/rcon-battery.py phase2 --port 25575     # assert what survived, then stop

Between the phases the caller may null out config keys (see --null-config) to exercise the
nulled-key regression. Exits non-zero on the first failed assertion.
"""
import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rcon import rcon  # noqa: E402

PROBE = "mcprobe"
FAILURES = []


def send(args, command):
    return rcon(command, host=args.host, port=args.port, password=args.password)


def check(label, condition, detail=""):
    if condition:
        print(f"  PASS  {label}")
    else:
        print(f"  FAIL  {label}" + (f"\n        {detail}" if detail else ""))
        FAILURES.append(label)
    return condition


def count_entities(args, selector):
    """Exact entity count, via a scoreboard probe — `execute if entity` only reports pass/fail."""
    send(args, f"scoreboard objectives add {PROBE} dummy")
    send(args, f"execute store result score probe {PROBE} if entity {selector}")
    reply = send(args, f"scoreboard players get probe {PROBE}")
    for token in reply.replace("[", " ").replace("]", " ").split():
        if token.isdigit():
            return int(token)
    return -1


def phase1(args):
    print("phase 1 — build, activate, exercise commands")

    # Nobody is online, so nothing keeps spawn chunks resident and the structure would be built
    # into a chunk that immediately unloads. Forceload is saved with the world, so it also keeps
    # the chunk resident across the restart phase 2 depends on.
    send(args, "forceload add 0 0")
    time.sleep(1)

    reply = send(args, "mobconduit build 0 100 0")
    check("build command reports a full frame", "42-block frame" in reply, reply)

    time.sleep(4)  # validation runs on the crystal tick, at most 40 ticks out

    status = send(args, "mobconduit status")
    check("one conduit active after build", "in this dimension: 1" in status, status)
    check("radius reported at full frame", "radius 128" in status, status)

    base = send(args, "execute if block 0 100 0 minecraft:light")
    check("obsidian base swapped for a light block", "passed" in base.lower(), base)

    holograms = count_entities(args, "@e[type=minecraft:text_display,x=0,y=104,z=0,distance=..4]")
    check("exactly one hologram above the crystal", holograms == 1, f"found {holograms}")

    for command, expect in [
        ("mobconduit sweep", "conduit"),
        ("mobconduit visualize", "coverage"),
        ("mobconduit reload", "reloaded"),
        ("mobconduit get frame_block", "frame_block"),
        ("mobconduit set radius_max 96", "radius_max"),
    ]:
        reply = send(args, command)
        check(f"`{command}` answers cleanly", expect in reply.lower(), reply)

    reply = send(args, "mobconduit get radius_max")
    check("set then get round-trips", "96" in reply, reply)

    reply = send(args, "mobconduit set radius_max 128")
    check("radius_max restored", "128" in reply, reply)

    reply = send(args, "mobconduit set frame_block minecraft:not_a_real_block")
    check("unknown block falls back to the default", "netherite_block" in reply, reply)

    reply = send(args, "mobconduit set nonsense_key 5")
    check("unknown key is rejected", "unknown setting" in reply.lower(), reply)

    time.sleep(2)
    send(args, "mobconduit status off")
    print("  ...stopping the server")
    send(args, "stop")


def phase2(args):
    print("phase 2 — what survived the restart")

    # Note: this is the weaker half of the persistence test — by the time RCON connects the
    # crystal may already have re-derived the conduit, which would mask a wipe. `check-persistence`
    # reads the store file directly and is the assertion that actually pins the regression.
    status = send(args, "mobconduit status")
    check("conduit persisted across a clean shutdown", "in this dimension: 1" in status, status)

    holograms = count_entities(args, "@e[type=minecraft:text_display,x=0,y=104,z=0,distance=..4]")
    check("still exactly one hologram, not a duplicate", holograms == 1, f"found {holograms}")

    reply = send(args, "mobconduit get suppression_feedback")
    check("a nulled config key is still reachable", "unknown setting" not in reply.lower(), reply)
    check("a nulled config key fell back to its default", "actionbar" in reply, reply)

    reply = send(args, "mobconduit set suppression_feedback particle")
    check("a nulled config key is settable again", "particle" in reply, reply)

    reply = send(args, "mobconduit get disabled_dimensions")
    check("a nulled list key is still reachable", "unknown setting" not in reply.lower(), reply)

    time.sleep(1)
    print("  ...stopping the server")
    send(args, "stop")


def check_persistence(path):
    """Reads the on-disk conduit store directly.

    The sharpest possible test of the shutdown teardown: after a clean stop the store must still
    name the conduit. Asserting this over RCON after a restart is weaker, because the crystal's
    own validation re-derives the conduit within 40 ticks and would mask a wipe.

    Handles both shapes — the Bukkit plugin's JSON and the Fabric/NeoForge gzipped NBT, which is
    scanned for the marker bytes rather than fully parsed.
    """
    store = Path(path)
    if not check("store file exists", store.is_file(), str(store)):
        return

    if store.suffix == ".json":
        data = json.loads(store.read_text())
        conduits = data.get("conduits") or []
        check("store records at least one conduit", len(conduits) > 0, store.read_text()[:400])
        if conduits:
            check("recorded conduit has a frame count", conduits[0].get("frame_count", 0) > 0, str(conduits[0]))
        return

    import gzip
    raw = gzip.open(store, "rb").read()
    # An empty list serialises as TAG_List "conduits" with element type 0 and length 0.
    empty = b"\x09\x00\x08conduits\x00\x00\x00\x00\x00"
    check("store does not hold an empty conduit list", empty not in raw, repr(raw[:120]))
    check("store mentions a frame count", b"frame_count" in raw, repr(raw[:200]))


def null_config(path):
    """Writes explicit JSON nulls, to exercise the nulled-key regression on the next boot."""
    config = json.loads(Path(path).read_text())
    for key in ("suppression_feedback", "disabled_dimensions", "removal_particle"):
        config[key] = None
    Path(path).write_text(json.dumps(config, indent=2))
    print(f"nulled suppression_feedback, disabled_dimensions and removal_particle in {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["phase1", "phase2", "null-config", "check-persistence"])
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", default="mobconduit")
    parser.add_argument("--config", help="config path, for null-config")
    parser.add_argument("--store", help="conduit store path, for check-persistence")
    args = parser.parse_args()

    if args.phase == "null-config":
        null_config(args.config)
        return 0

    if args.phase == "check-persistence":
        print("checking the on-disk conduit store")
        check_persistence(args.store)
        print()
        if FAILURES:
            print(f"FAILED: {len(FAILURES)} check(s) — {', '.join(FAILURES)}")
            return 1
        print("all checks passed")
        return 0

    (phase1 if args.phase == "phase1" else phase2)(args)

    print()
    if FAILURES:
        print(f"FAILED: {len(FAILURES)} check(s) — {', '.join(FAILURES)}")
        return 1

    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
