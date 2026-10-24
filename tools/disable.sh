#!/usr/bin/env bash
# Disable a list of packages in small batches, screenshotting between batches.
#   tools/disable.sh lists/batch1.txt          (5 per batch, asks after each)
#   BATCH=3 tools/disable.sh lists/batch1.txt
# Uses `pm disable-user --user 0` only; nothing is ever uninstalled. Undo: tools/restore.sh
source "$(dirname "$0")/common.sh"
need_tv

list="${1:?Usage: tools/disable.sh <list file>}"
BATCH="${BATCH:-5}"
[ -f "$STATE/disabled.txt" ] || : > "$STATE/disabled.txt"
[ -n "$(ls "$ROOT"/backup 2>/dev/null)" ] || { echo "Run tools/backup.sh first."; exit 2; }

pkgs="$(read_list "$list")"
[ -n "$pkgs" ] || { echo "No packages in $list (lines starting with # are ignored)."; exit 0; }

# Refuse the whole list if any entry is something the TV needs to boot or work.
bad=""
for p in $pkgs; do is_core "$p" && bad="$bad $p"; done
if [ -n "$bad" ]; then
    echo "REFUSED: this list contains packages the TV needs to boot or work:"
    for p in $bad; do echo "  $p"; done
    echo "Remove them from $list and run again. Nothing was changed."
    exit 3
fi

home="$(tvsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -1 || true)"
if [ -z "$home" ] || echo "$home" | grep -Eq 'Unknown command|No activity|Error'; then
    home="$(tvsh dumpsys activity activities | grep -E 'HOME|Launcher' | head -1 || true)"
    [ -n "$home" ] || echo "WARNING: could not ask the TV which app is Home. Nothing about Home was checked."
fi
for p in $pkgs; do
    if echo "$home" | grep -q "$p/"; then
        echo "REFUSED: $p is the current Home app. Install TV Home and use tools/stock-launcher.sh instead."
        exit 3
    fi
    if [ "$p" = "com.google.android.tungsten.setupwraith" ]; then
        echo "REFUSED: use tools/stock-launcher.sh for the setup wizard (it checks the saved Wi-Fi network first)."
        exit 3
    fi
done

installed="$(tvsh pm list packages -u)"
n=0; batch=1; done_ok=0; tried=0; this_batch=""
for p in $pkgs; do
    if ! echo "$installed" | grep -x "package:$p" >/dev/null; then echo "skip  $p (not installed)"; continue; fi
    if is_disabled "$p"; then echo "skip  $p (already disabled)"; continue; fi
    tried=$((tried + 1))
    out="$(tvsh pm disable-user --user 0 "$p" 2>&1 || true)"
    # Trust the package list, not the exit code: some firmware reports success and changes nothing.
    if is_disabled "$p"; then
        echo "off   $p"
        grep -x "$p" "$STATE/disabled.txt" >/dev/null || echo "$p" >> "$STATE/disabled.txt"
        echo "$(date '+%Y-%m-%d %H:%M:%S') disabled $p" >> "$STATE/disabled.log"
        done_ok=$((done_ok + 1)); this_batch="$this_batch $p"
    else
        echo "FAIL  $p: $out"
    fi
    if [ "$tried" -ge 3 ] && [ "$done_ok" -eq 0 ]; then
        echo
        echo "STOP: the TV refused every package so far. This firmware does not allow disabling apps"
        echo "(Fire OS 5 and some others do this). Retrying won't help. Fall back to the TV's own privacy"
        echo "settings and DNS blocking (tools/dns.sh, README)."
        exit 4
    fi
    n=$((n + 1))
    if [ "$n" -ge "$BATCH" ]; then
        n=0
        shot="$SHOTS/disable-batch$batch.png"
        adbx shell input keyevent KEYCODE_HOME; sleep 2
        screenshot "$shot" || true
        echo "Batch $batch done:$this_batch"
        echo "Look at the TV: Home, the remote, the picture. Press Enter to go on, or type u + Enter to undo this batch."
        { read -r ans </dev/tty; } 2>/dev/null || ans=""
        if [ "$ans" = "u" ]; then
            for q in $this_batch; do tvsh pm enable --user 0 "$q" >/dev/null 2>&1 || true; echo "on    $q"; done
            echo "Batch undone. Split it up to find the one that matters."
            exit 0
        fi
        batch=$((batch + 1)); this_batch=""
    fi
done
echo
echo "Disabled $done_ok package(s). Full record: state/disabled.txt   Undo everything: tools/restore.sh"
echo "After the last batch: tools/reboot-check.sh"
