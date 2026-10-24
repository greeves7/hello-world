#!/usr/bin/env bash
# Undo: re-enable everything this toolkit disabled, put Home and Private DNS back.
#   tools/restore.sh                  re-enable what state/disabled.txt lists
#   tools/restore.sh --to-backup      also re-enable anything disabled now that was enabled in the first backup
#   tools/restore.sh <package>        re-enable one package
source "$(dirname "$0")/common.sh"
need_tv

enable_one() {
    tvsh pm enable --user 0 "$1" >/dev/null 2>&1 || tvsh pm enable "$1" >/dev/null 2>&1 || true
    if is_disabled "$1"; then echo "FAIL  $1 is still disabled"; return 1; fi
    echo "on    $1"
    if [ -f "$STATE/disabled.txt" ]; then
        grep -vx "$1" "$STATE/disabled.txt" > "$STATE/disabled.tmp" || true
        mv "$STATE/disabled.tmp" "$STATE/disabled.txt"
    fi
    echo "$(date '+%Y-%m-%d %H:%M:%S') enabled $1" >> "$STATE/disabled.log"
}

if [ $# -eq 1 ] && [ "$1" != "--to-backup" ]; then enable_one "$1"; exit; fi

# Stock launcher and setup wizard first, so Home always has somewhere to go.
for p in $(cat "$STATE/stock-launchers.txt" 2>/dev/null || true); do enable_one "$p" || true; done
for p in $(cat "$STATE/disabled.txt" 2>/dev/null || true); do enable_one "$p" || true; done

if [ "${1:-}" = "--to-backup" ] && [ -f "$STATE/first-backup" ]; then
    ref="$(cat "$STATE/first-backup")/packages-disabled.txt"
    now="$(tvsh pm list packages -d)"
    for line in $now; do
        grep -x "$line" "$ref" >/dev/null || enable_one "${line#package:}" || true
    done
fi

if [ -f "$STATE/stock-home.txt" ]; then
    comp="$(cat "$STATE/stock-home.txt")"
    echo "Home back to $comp"
    tvsh cmd package set-home-activity "$comp" || true
fi
if [ -f "$STATE/dns-prev.txt" ]; then
    # shellcheck disable=SC1091
    . "$STATE/dns-prev.txt"
    tvsh settings put global private_dns_mode "${prev_mode:-opportunistic}"
    if [ -n "${prev_spec:-}" ] && [ "$prev_spec" != "null" ]; then
        tvsh settings put global private_dns_specifier "$prev_spec"
    fi
    echo "Private DNS back to ${prev_mode:-opportunistic}"
fi
acc="$(tvsh settings get secure enabled_accessibility_services)"
case "$acc" in *"$PKG"*) "$TOOLS/keys.sh" off || true ;; esac
echo
echo "Restored. TV Home stays installed; remove it with: tools/tv.sh pm uninstall $PKG"
echo "Reboot the TV once to be sure: tools/tv.sh reboot"
