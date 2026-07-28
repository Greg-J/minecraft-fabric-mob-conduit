#!/usr/bin/env python3
"""Unpack Loom's decompiled Minecraft sources into ./.minecraft-src/ as a
greppable net/minecraft/... tree.

Run ./gradlew genSources FIRST. That populates Loom's decompile cache, which is
content-addressed (files are named by SHA-256, not by class), so it cannot be
grepped directly. This script turns that cache into a real package tree.

The output is proprietary Mojang code. It is gitignored and must stay that way.
"""

import os
import shutil
import struct
import sys
import zipfile

CACHE = os.path.expanduser('~/.gradle/caches/fabric-loom/decompile/v1.zip')
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, '.minecraft-src')


def parse(blob):
    """Blob layout: 'LOOM' <4b> 'NAME' <u32 len> <name> 'SRC ' <u32 len> <source>"""
    if not blob.startswith(b'LOOM'):
        return None, 'no LOOM magic'
    i = blob.find(b'NAME')
    if i < 0:
        return None, 'no NAME tag'
    p = i + 4
    (nlen,) = struct.unpack_from('>I', blob, p)
    p += 4
    name = blob[p:p + nlen].decode('utf-8')
    p += nlen
    if blob[p:p + 4] != b'SRC ':
        return None, 'expected SRC tag, got %r' % blob[p:p + 8]
    p += 4
    (slen,) = struct.unpack_from('>I', blob, p)
    p += 4
    raw = blob[p:p + slen]
    if len(raw) != slen:
        return None, 'truncated source'
    return (name, raw.decode('utf-8')), None


def main():
    if not os.path.exists(CACHE):
        sys.exit('Decompile cache not found: %s\nRun ./gradlew genSources first.' % CACHE)

    parsed, errors = [], []
    with zipfile.ZipFile(CACHE) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            res, err = parse(z.read(info))
            (errors if err else parsed).append((info.filename, err) if err else res)

    unsafe = [n for n, _ in parsed if n.startswith('/') or '..' in n.split('/')]
    if errors or unsafe:
        for fn, err in errors[:5]:
            print('FAIL %s: %s' % (fn, err), file=sys.stderr)
        sys.exit('Aborted: %d parse failures, %d unsafe paths. Nothing written.'
                 % (len(errors), len(unsafe)))

    # Wipe first: on a version bump, classes deleted upstream would otherwise
    # linger and silently answer greps with code that no longer exists.
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)

    for name, src in parsed:
        dest = os.path.join(OUT, name + '.java')
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, 'w', encoding='utf-8') as f:
            f.write(src)

    print('Wrote %d .java files to %s' % (len(parsed), OUT))


if __name__ == '__main__':
    main()
