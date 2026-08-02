#!/usr/bin/env python3
"""Minimal RCON client for driving the dev server. Usage: python3 tools/rcon.py "command" """
import socket
import struct
import sys


def rcon(command, host="127.0.0.1", port=25575, password="mobconduit"):
    def packet(req_id, req_type, payload):
        body = struct.pack("<ii", req_id, req_type) + payload.encode() + b"\x00\x00"
        return struct.pack("<i", len(body)) + body

    with socket.create_connection((host, port), timeout=10) as sock:
        sock.sendall(packet(1, 3, password))
        response = sock.recv(4096)
        _, _, resp_type = struct.unpack("<iii", response[:12])
        if resp_type == -1:
            raise RuntimeError("RCON auth failed")

        sock.sendall(packet(2, 2, command))
        data = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            data += chunk
            # A full response is length-prefixed; keep reading until we have it all.
            if len(data) >= 4:
                (length,) = struct.unpack("<i", data[:4])
                if len(data) >= 4 + length:
                    break
        return data[12:-2].decode(errors="replace")


if __name__ == "__main__":
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25575
    print(rcon(sys.argv[1], port=port))
