#!/usr/bin/env python3
"""Upload a built jar to Modrinth as a new version.

Driven by environment variables so it works identically from CI and from a
local shell:

    MODRINTH_TOKEN       personal access token with VERSION_CREATE scope
    MODRINTH_PROJECT_ID  project id or slug
    JAR_PATH             jar to upload
    MOD_VERSION          version number, e.g. 1.0.1
    MC_VERSION           Minecraft version, e.g. 26.2
    CHANGELOG            release notes, markdown
    VERSION_TYPE         release | beta | alpha   (default release)
    DRY_RUN              true to validate and print without uploading

Run locally with:

    MODRINTH_TOKEN=... MODRINTH_PROJECT_ID=... \\
    JAR_PATH=build/libs/mob-conduit-1.0.0.jar MOD_VERSION=1.0.0 \\
    MC_VERSION=26.2 CHANGELOG="..." python3 tools/publish-modrinth.py

Uses only the standard library so CI needs no pip install step.
"""

import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "https://api.modrinth.com/v2"

# Modrinth asks projects to identify themselves so they can get in touch about
# a misbehaving script rather than just blocking it.
USER_AGENT = "Greg-J/minecraft-fabric-mob-conduit/publish-script"

LOADERS = ["fabric", "neoforge", "paper", "spigot"]


def require(name):
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"error: {name} is not set")
    return value


def encode_multipart(fields, files):
    """Builds a multipart/form-data body. Returns (content_type, body)."""
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


def api_get(path, token):
    request = urllib.request.Request(
        f"{API}{path}",
        headers={"Authorization": token, "User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def api_patch(path, token, payload):
    request = urllib.request.Request(
        f"{API}{path}",
        data=json.dumps(payload).encode(),
        method="PATCH",
        headers={
            "Authorization": token,
            "User-Agent": USER_AGENT,
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.status


def ensure_project_loaders(project, token):
    """Adds the loaders this release needs to the project's supported list.

    A version's loaders must be a subset of the project's, and a 'mod' project can carry
    plugin loaders (Pl3xMap does exactly this), so one project hosts every platform.
    """
    current = set(project["loaders"])
    missing = [loader for loader in LOADERS if loader not in current]

    if not missing:
        return

    print(f"project : adding loaders {missing} (had {sorted(current)})")
    api_patch(f"/project/{project['id']}", token, {"loaders": sorted(current | set(LOADERS))})


def main():
    token = require("MODRINTH_TOKEN")
    project_id = require("MODRINTH_PROJECT_ID")
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

    # Fail before uploading rather than on a 400 from the API. Republishing an
    # existing version number is the most likely mistake here.
    project = api_get(f"/project/{project_id}", token)
    existing = api_get(f"/project/{project_id}/version", token)
    taken = {v["version_number"] for v in existing}

    print(f"project : {project['title']} ({project['slug']}, {project['id']})")
    print(f"versions: {len(existing)} published")
    print(f"jar     : {jar_path} ({jar_path.stat().st_size / 1024:.0f} KB)")
    print(f"version : {mod_version} [{version_type}] for MC {mc_version}")

    if mod_version in taken:
        # Not an error: the pipeline has multiple targets (Modrinth, CurseForge), and a
        # re-run to fill one that failed must not be blocked by the ones that worked.
        print(f"version {mod_version} already exists on Modrinth; skipping")
        return

    ensure_project_loaders(project, token)

    metadata = {
        "project_id": project["id"],
        "version_number": mod_version,
        "name": f"{project['title']} {mod_version}",
        "changelog": changelog,
        "game_versions": [mc_version],
        "loaders": LOADERS,
        "version_type": version_type,
        "featured": True,
        "dependencies": [
            # Fabric API. Required, and Modrinth surfaces it to players as a
            # dependency they must install.
            {"project_id": "P7dR8mSH", "dependency_type": "required"}
        ],
        "file_parts": ["file"],
        "primary_file": "file",
    }

    if dry_run:
        print("\n--- dry run, not uploading ---")
        print(json.dumps(metadata, indent=2))
        return

    content_type, body = encode_multipart({"data": json.dumps(metadata)}, {"file": str(jar_path)})
    request = urllib.request.Request(
        f"{API}/version",
        data=body,
        method="POST",
        headers={
            "Authorization": token,
            "User-Agent": USER_AGENT,
            "Content-Type": content_type,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            created = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        sys.exit(f"error: Modrinth returned {error.code}\n{detail}")

    print(f"\npublished {created['version_number']} as {created['id']}")
    print(f"https://modrinth.com/mod/{project['slug']}/version/{created['version_number']}")


if __name__ == "__main__":
    main()
