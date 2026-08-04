#!/usr/bin/env python3
"""Upload a built jar to CurseForge as a new file.

Driven by environment variables, same shape as publish-modrinth.py:

    CURSEFORGE_TOKEN       API token from curseforge.com account settings
    CURSEFORGE_PROJECT_ID  numeric project id, shown on the project page
    JAR_PATH               jar to upload
    MOD_VERSION            version number, e.g. 1.0.1
    MC_VERSION             Minecraft version, e.g. 26.2
    CHANGELOG              release notes, markdown
    VERSION_TYPE           release | beta | alpha   (default release)
    DRY_RUN                true to resolve ids and print without uploading

CurseForge differs from Modrinth in two ways that matter here. It
authenticates with an X-Api-Token header rather than Authorization, and it
identifies game versions, loaders and Java versions by opaque numeric ids
rather than by name. Those ids are resolved at runtime instead of hardcoded,
so bumping minecraft_version does not silently upload against the wrong one.

Uses only the standard library.
"""

import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "https://minecraft.curseforge.com/api"

USER_AGENT = "Greg-J/minecraft-universal-mob-conduit/publish-script"

# Loader-named entries that actually exist in CurseForge's taxonomy. Plugin-side compat is
# handled separately in resolve_game_versions via the bukkit-typed version entry — there is
# no loader entry named "Bukkit", "Paper" or "Spigot" (2026-08-03 upload attempts).
LOADER_NAMES = ["Fabric", "NeoForge"]
JAVA_NAME = "Java 25"

# CurseForge rejects an upload that carries no entry from the Environment group
# with errorCode 1021. This mod is server side only.
ENVIRONMENT_NAME = "Server"
ENVIRONMENT_TYPE_SLUG = "environment"


def require(name):
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"error: {name} is not set")
    return value


def api_get(path, token):
    request = urllib.request.Request(
        f"{API}{path}",
        headers={"X-Api-Token": token, "User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def resolve_game_versions(token, mc_version):
    """Maps human names to CurseForge's numeric ids.

    Several entries can share a name across different version types: 26.2
    exists under both the Java Edition type and an unrelated one. Preferring
    the type whose slug is minecraft-<version> picks the Java Edition entry
    rather than whichever happens to come first in the list.
    """
    versions = api_get("/game/versions", token)
    types = {t["id"]: t for t in api_get("/game/version-types", token)}

    wanted_type_slug = "minecraft-" + mc_version.replace(".", "-")
    candidates = [v for v in versions if v["name"] == mc_version]

    if not candidates:
        sys.exit(f"error: CurseForge has no game version named {mc_version!r}")

    preferred = [
        v for v in candidates
        if types.get(v["gameVersionTypeID"], {}).get("slug") == wanted_type_slug
    ]
    game_version = (preferred or candidates)[0]

    if not preferred and len(candidates) > 1:
        print(
            f"warning: {len(candidates)} versions named {mc_version}, none under "
            f"type {wanted_type_slug}; using id {game_version['id']}"
        )

    def by_name(name, type_slug=None):
        for v in versions:
            if v["name"] != name:
                continue
            if type_slug and types.get(v["gameVersionTypeID"], {}).get("slug") != type_slug:
                continue
            return v

        # List near-matches so a taxonomy change shows up in the log instead of a bare exit.
        near = sorted({v["name"] for v in versions if name.lower() in v["name"].lower()
                       or v["name"].lower() in name.lower()})
        sys.exit(f"error: CurseForge has no version entry named {name!r}; near matches: {near}")

    loaders = [by_name(name) for name in LOADER_NAMES]
    java = by_name(JAVA_NAME)
    environment = by_name(ENVIRONMENT_NAME, ENVIRONMENT_TYPE_SLUG)

    print(f"minecraft {mc_version}: id {game_version['id']}")
    for name, loader in zip(LOADER_NAMES, loaders):
        print(f"{name}: id {loader['id']}")
    print(f"{JAVA_NAME}: id {java['id']}")
    print(f"{ENVIRONMENT_NAME}: id {environment['id']}")

    ids = [game_version["id"], *(loader["id"] for loader in loaders), java["id"], environment["id"]]

    # Plugin-side compatibility has no loader-named entry ("Bukkit", "Paper" and "Spigot"
    # simply do not exist — 2026-08-03 upload attempts). The taxonomy instead carries the
    # Minecraft version under a bukkit-flavoured version type, so find every entry named
    # like the MC version whose type mentions bukkit.
    bukkit_types = {t["id"]: t["slug"] for t in types.values() if "bukkit" in t["slug"].lower()}
    bukkit_versions = [
        v for v in candidates
        if types.get(v["gameVersionTypeID"], {}).get("id") in bukkit_types
    ]

    if bukkit_versions:
        for v in bukkit_versions:
            slug = bukkit_types[v["gameVersionTypeID"]]
            print(f"{mc_version} ({slug}): id {v['id']}")
            ids.append(v["id"])
    else:
        print(f"warning: no bukkit-typed version entry for {mc_version}; "
              f"known type slugs: {sorted(t['slug'] for t in types.values())}")

    return ids


def encode_multipart(fields, files):
    boundary = uuid.uuid4().hex
    parts = []

    for name, value in fields.items():
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        parts.append(value.encode("utf-8"))
        parts.append(b"\r\n")

    for name, path in files.items():
        filename = Path(path).name
        ctype = mimetypes.guess_type(filename)[0] or "application/octet-stream"
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(
            f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode()
        )
        parts.append(f"Content-Type: {ctype}\r\n\r\n".encode())
        parts.append(Path(path).read_bytes())
        parts.append(b"\r\n")

    parts.append(f"--{boundary}--\r\n".encode())
    return f"multipart/form-data; boundary={boundary}", b"".join(parts)


def main():
    token = require("CURSEFORGE_TOKEN")
    project_id = require("CURSEFORGE_PROJECT_ID")
    jar_path = Path(require("JAR_PATH"))
    mod_version = require("MOD_VERSION")
    mc_version = require("MC_VERSION")
    changelog = os.environ.get("CHANGELOG", "").strip()
    version_type = os.environ.get("VERSION_TYPE", "release").strip() or "release"
    dry_run = os.environ.get("DRY_RUN", "false").strip().lower() == "true"

    if version_type not in ("release", "beta", "alpha"):
        sys.exit(f"error: VERSION_TYPE must be release, beta or alpha, got {version_type!r}")

    if not jar_path.is_file():
        sys.exit(f"error: {jar_path} does not exist")

    print(f"project : {project_id}")
    print(f"jar     : {jar_path} ({jar_path.stat().st_size / 1024:.0f} KB)")
    print(f"version : {mod_version} [{version_type}] for MC {mc_version}")

    game_versions = resolve_game_versions(token, mc_version)

    metadata = {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": f"Mob Conduit {mod_version}",
        "gameVersions": game_versions,
        "releaseType": version_type,
        "relations": {
            "projects": [{"slug": "fabric-api", "type": "requiredDependency"}]
        },
    }

    if dry_run:
        print("\n--- dry run, not uploading ---")
        print(json.dumps(metadata, indent=2))
        return

    content_type, body = encode_multipart(
        {"metadata": json.dumps(metadata)}, {"file": str(jar_path)}
    )
    request = urllib.request.Request(
        f"{API}/projects/{project_id}/upload-file",
        data=body,
        method="POST",
        headers={
            "X-Api-Token": token,
            "User-Agent": USER_AGENT,
            "Content-Type": content_type,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            created = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        sys.exit(f"error: CurseForge returned {error.code}\n{detail}")

    print(f"\npublished as file {created['id']}")


if __name__ == "__main__":
    main()
