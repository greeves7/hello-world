#!/usr/bin/env bash
# Reboot once after the last batch and compare what is disabled with what we disabled.
# Some firmware re-enables packages at every boot from its own list; note those, don't fight them.
source "$(dirname "$0")/common.sh"
need_tv

echo "Rebooting the TV..."
adbx reboot || true
sleep 20
for _ in $(seq 1 60); do
    adb connect "$(serial)" >/dev/null 2>&1 || true
    [ "$(tvsh getprop sys.boot_completed 2>/dev/null || true)" = "1" ] && break
    sleep 5
done
[ "$(tvsh getprop sys.boot_completed 2>/dev/null || true)" = "1" ] || {
    echo "TV did not come back on ADB. Check the screen; if Wi-Fi changed, reconnect with tools/connect.sh."; exit 1; }
sleep 10

now="$(tvsh pm list packages -d | sed 's/^package://')"
back=""
for p in $(cat "$STATE/disabled.txt" "$STATE/stock-launchers.txt" 2>/dev/null || true); do
    echo "$now" | grep -x "$p" >/dev/null || back="$back $p"
done
if [ -n "$back" ]; then
    echo "Re-enabled by the firmware at boot (note them, don't keep disabling them):"
    for p in $back; do echo "  $p"; echo "# re-enabled at boot by firmware: $p" >> "$STATE/reenabled-at-boot.txt"; done
else
    echo "Everything we disabled is still disabled."
fi

adbx shell input keyevent KEYCODE_HOME; sleep 3
f="$(focused_app)"; echo "$f"
screenshot "$SHOTS/after-reboot.png" || true
if ! echo "$f" | grep -q "$PKG"; then
    rec="$(tvsh logcat -d | grep RecoveryActivity | tail -3 || true)"
    [ -n "$rec" ] && { echo "Setup wizard RecoveryActivity is active:"; echo "$rec"; }
    echo "Home is not TV Home after the reboot. If the screen is black: tools/stock-launcher.sh on"
fi
