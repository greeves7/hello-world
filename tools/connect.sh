#!/usr/bin/env bash
# Connect to the TV over network ADB and remember its address.
#   tools/connect.sh 192.168.1.50          (port 5555)
#   TV_PORT=5877 tools/connect.sh 192.168.1.50
source "$(dirname "$0")/common.sh"

TV_HOST="${1:-${TV_HOST:-}}"
[ -n "$TV_HOST" ] || { echo "Usage: tools/connect.sh <tv ip>"; exit 2; }
need_tv

echo "Connecting to $(serial)..."
echo ">>> Watch the TV now: an 'Allow USB debugging?' prompt should appear. Tick 'Always allow' and press OK."
adb connect "$(serial)" || true
for _ in $(seq 1 30); do
    state="$(adb -s "$(serial)" get-state 2>/dev/null || true)"
    [ "$state" = "device" ] && break
    sleep 2
done
if [ "${state:-}" != "device" ]; then
    echo "Not connected (state: ${state:-none})."
    echo "- 'unauthorized': accept the prompt on the TV, then run this again."
    echo "- 'Connection refused': network debugging is off, or the TV uses Wireless debugging/another port (see README, Connect)."
    echo "- macOS 'No route to host' with the port open: Local Network privacy is blocking adb (see README)."
    exit 1
fi
echo "$TV_HOST" > "$STATE/tv_host"
echo "Connected. TV:"
echo "  manufacturer: $(tvsh getprop ro.product.manufacturer)"
echo "  model:        $(tvsh getprop ro.product.model)"
echo "  android:      $(tvsh getprop ro.build.version.release) (SDK $(sdk_level))"
echo "  build:        $(tvsh getprop ro.build.id)"
echo "  adb secure:   $(tvsh getprop ro.adb.secure)   (0 = anyone on your network can get a shell while 5555 is open)"
