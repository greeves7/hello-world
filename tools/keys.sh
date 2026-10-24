#!/usr/bin/env bash
# Optional remote-button helper (KeyService). Turn it on only if a button stopped working after
# the stock launcher was disabled.
#   tools/keys.sh on | off | status
#   tools/keys.sh scan      show raw button codes (press the button, Ctrl-C to stop)
#
# Trade-off: while a key-filtering accessibility service is bound, some firmware delivers held
# buttons as single presses. Test holding Volume Up before and after; if it stops ramping, you
# are choosing between that and the remapped buttons.
source "$(dirname "$0")/common.sh"
need_tv
SVC="$PKG/$PKG.KeyService"
cur="$(tvsh settings get secure enabled_accessibility_services)"
[ "$cur" = "null" ] && cur=""

case "${1:-status}" in
    on)
        echo "Before: hold Volume Up on the remote for 2 s. Does it ramp up smoothly?"
        confirm "Enable the remote-button helper?" || exit 0
        case ":$cur:" in *":$SVC:"*) ;; *) cur="${cur:+$cur:}$SVC" ;; esac
        tvsh settings put secure enabled_accessibility_services "$cur"
        tvsh settings put secure accessibility_enabled 1
        sleep 2
        bound="$(tvsh dumpsys accessibility | grep -c "$PKG" || true)"
        echo "Helper enabled (bound entries: $bound)."
        echo "Now press the real buttons (an injected key proves nothing), then hold Volume Up again."
        echo "If holding stopped repeating, that is the trade-off, not a broken remote: tools/keys.sh off" ;;
    off)
        new="$(echo "$cur" | tr ':' '\n' | grep -vx "$SVC" | paste -sd: - || true)"
        if [ -z "$new" ]; then
            tvsh settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
        else
            tvsh settings put secure enabled_accessibility_services "$new"
        fi
        echo "Helper off." ;;
    status)
        case ":$cur:" in *":$SVC:"*) echo "Helper: on" ;; *) echo "Helper: off" ;; esac
        echo "enabled_accessibility_services=$cur" ;;
    scan)
        echo "Press the button now. KEY_UNKNOWN / KEY_PROGRAM buttons need settings_key_scancodes (MSC_SCAN value)."
        adbx shell getevent -lt ;;
    *) echo "Usage: tools/keys.sh on|off|status|scan"; exit 2 ;;
esac
