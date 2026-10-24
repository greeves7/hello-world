#!/usr/bin/env bash
# Hand Home over to TV Home for good by disabling the stock launcher (and, if needed, the setup
# wizard), or put them back.
#   tools/stock-launcher.sh off
#   tools/stock-launcher.sh on
source "$(dirname "$0")/common.sh"
need_tv

WIZARD="com.google.android.tungsten.setupwraith"
mode="${1:?Usage: tools/stock-launcher.sh off|on}"

if [ "$mode" = "on" ]; then
    for p in $(cat "$STATE/stock-launchers.txt" 2>/dev/null || true); do
        tvsh pm enable --user 0 "$p" >/dev/null 2>&1 || true
        echo "on    $p"
    done
    : > "$STATE/stock-launchers.txt"
    if [ -f "$STATE/stock-home.txt" ]; then tvsh cmd package set-home-activity "$(cat "$STATE/stock-home.txt")" || true; fi
    adbx shell input keyevent KEYCODE_HOME
    echo "Stock launcher back. TV Home is still installed and can be picked again with tools/install-launcher.sh."
    exit 0
fi

is_installed "$PKG" || { echo "Install TV Home first: tools/install-launcher.sh"; exit 2; }
if echo "$(tvsh pm list packages | sed 's/^package://')" | grep -E '^com\.cvte\.' >/dev/null; then
    echo "This is a CVTE board: leave its stock launcher enabled. Use set-home-activity (install-launcher.sh),"
    echo "reboot once and confirm Home and ADB come back. Nothing changed."
    exit 3
fi

claimers="$(tvsh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | cut -d/ -f1 | sort -u)"
echo "Apps that claim Home:"; echo "$claimers" | sed 's/^/  /'
targets=""
for p in $claimers; do
    case "$p" in
        "$PKG"|com.android.tv.settings|com.android.settings) ;;  # Settings' FallbackHome must stay
        "$WIZARD") ;;                                              # only if it takes over, below
        *) targets="$targets $p" ;;
    esac
done
[ -n "$targets" ] || echo "No stock launcher to disable."

for p in $targets; do
    is_core "$p" && { echo "skip  $p (core)"; continue; }
    confirm "Disable stock launcher $p?" || continue
    tvsh pm disable-user --user 0 "$p" >/dev/null 2>&1 || true
    if is_disabled "$p"; then
        echo "off   $p"; echo "$p" >> "$STATE/stock-launchers.txt"
    else
        echo "FAIL  $p is still enabled"
    fi
done

tvsh cmd package set-home-activity "$HOME_COMPONENT" >/dev/null 2>&1 || true
adbx shell input keyevent KEYCODE_HOME; sleep 3
f="$(focused_app)"; echo "$f"

if ! echo "$f" | grep -q "$PKG" && echo "$f" | grep -q "$WIZARD"; then
    echo
    echo "The setup wizard ($WIZARD) has taken Home."
    cname="$(tvsh dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u || true)"
    if echo "$cname" | grep -q "$WIZARD"; then
        echo "STOP: your saved Wi-Fi network was created by the setup wizard: disabling it makes Android"
        echo "delete that network, and the TV drops off Wi-Fi and ADB. First, on the TV: Settings > Network >"
        echo "your network > Forget, then join it again. Then run this script again."
        exit 3
    fi
    if confirm "Disable the setup wizard so TV Home gets the Home key?"; then
        tvsh pm disable-user --user 0 "$WIZARD" >/dev/null 2>&1 || true
        is_disabled "$WIZARD" && echo "$WIZARD" >> "$STATE/stock-launchers.txt"
        tvsh cmd package set-home-activity "$HOME_COMPONENT" >/dev/null 2>&1 || true
        adbx shell input keyevent KEYCODE_HOME; sleep 3
        f="$(focused_app)"; echo "$f"
    fi
fi

screenshot "$SHOTS/stock-launcher-off.png" || true
if echo "$f" | grep -q "$PKG"; then
    echo "OK: Home is TV Home."
    echo "Now press each remote button: the app buttons (Netflix, YouTube...), Input, Settings."
    echo "If one does nothing, see README 'Remote buttons' (tools/keys.sh) before enabling anything."
else
    echo "Home is not TV Home. Putting the stock launcher back."
    "$0" on
    exit 1
fi
