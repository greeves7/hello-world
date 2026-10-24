#!/usr/bin/env python3
"""Check that network ADB is really closed:  tools/adb-closed.py <tv ip> [port]

Only "connection refused" proves the port is closed. A timeout or "No route to host" proves
nothing (macOS Local Network privacy produces those too). Exit 0 = closed, 1 = OPEN, 2 = unsure.
"""
import socket
import sys


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    host = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
    try:
        with socket.create_connection((host, port), timeout=4):
            print(f"OPEN: {host}:{port} accepts connections. Switch off USB debugging and network debugging on the TV.")
            return 1
    except ConnectionRefusedError:
        print(f"closed: {host}:{port} refused the connection.")
        return 0
    except OSError as e:
        print(f"unsure: {host}:{port} -> {e}. This is not proof either way; check the TV is on and on this network.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
