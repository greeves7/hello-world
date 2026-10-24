#!/usr/bin/env bash
# Close network ADB when you are done. Port 5555 is plaintext; on ro.adb.secure=0 firmware anyone
# on your network can get a shell while it is open.
source "$(dirname "$0")/common.sh"
need_tv
echo "Final state:"
echo "  disabled by us: $(wc -l < "$STATE/disabled.txt" 2>/dev/null | tr -d ' ' || echo 0)"
echo "  home: $(focused_app | head -1)"
echo "  remote helper: $("$TOOLS/keys.sh" status | head -1)"
echo
echo "On the TV now:"
echo "  1. Settings > Device Preferences > Developer options > USB debugging: OFF"
echo "     (and Network debugging / ADB debugging / Wireless debugging: OFF if shown)"
echo "  2. Developer options > Revoke USB debugging authorisations"
echo "  3. Restart the TV"
echo "  4. If the network comes through a USB Ethernet adapter: unplug and replug it now, then"
echo "     check Settings > Network says Ethernet, not Wi-Fi. USB devices come back once debugging is off."
printf 'Press Enter when the TV has restarted... '
{ read -r _ </dev/tty; } 2>/dev/null || true
python3 "$TOOLS/adb-closed.py" "$TV_HOST" "$TV_PORT"
