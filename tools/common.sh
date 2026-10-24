# Shared helpers, sourced by the other scripts. Works with macOS bash 3.2 and Linux bash.
set -euo pipefail

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$TOOLS")"
STATE="$ROOT/state"      # what we changed: needed by restore.sh, keep it
SHOTS="$ROOT/shots"
mkdir -p "$STATE" "$SHOTS"

PKG="io.github.greeves7.tvhome"
HOME_COMPONENT="$PKG/.HomeActivity"

if [ -z "${TV_HOST:-}" ] && [ -f "$STATE/tv_host" ]; then TV_HOST="$(cat "$STATE/tv_host")"; fi
TV_PORT="${TV_PORT:-5555}"

need_tv() {
    if [ -z "${TV_HOST:-}" ]; then
        echo "No TV set. Run: tools/connect.sh <tv ip>   (or export TV_HOST=<tv ip>)" >&2
        exit 2
    fi
    command -v adb >/dev/null 2>&1 || {
        echo "adb not found. macOS: brew install android-platform-tools   Debian/Ubuntu: sudo apt install adb" >&2
        exit 2
    }
}

serial() { echo "$TV_HOST:$TV_PORT"; }

adbx() { need_tv; adb -s "$(serial)" "$@"; }

# One shell command on the TV, CR stripped. Keep pm commands to one per call.
tvsh() { adbx shell "$@" | tr -d '\r'; }

sdk_level() { tvsh getprop ro.build.version.sdk; }

# Capture first, then grep: with pipefail an early-exiting `grep -q` can make the pipeline fail.
is_disabled() { local l; l="$(tvsh pm list packages -d)"; echo "$l" | grep -x "package:$1" >/dev/null; }

is_installed() { local l; l="$(tvsh pm list packages)"; echo "$l" | grep -x "package:$1" >/dev/null; }

# Never disable these: the TV may stop booting (see README "What is never touched").
CORE_EXACT="android com.android.systemui com.android.shell com.android.phone com.android.settings
com.android.tv.settings com.android.permissioncontroller com.google.android.permissioncontroller
com.android.packageinstaller com.google.android.packageinstaller com.android.extservices
com.google.android.extservices com.android.ext.services com.google.android.ext.services
com.android.ext.shared com.google.android.ext.shared com.android.modulemetadata
com.google.android.modulemetadata com.android.vending com.google.android.gms com.google.android.gsf
com.android.inputdevices com.android.bluetooth com.android.providers.settings
com.android.providers.media com.android.providers.downloads com.android.providers.tv
com.android.location.fused com.android.keychain com.android.wallpaperbackup
com.google.android.tv.remote.service com.google.android.leanbackime com.google.android.inputmethod.latin
com.android.inputmethod.latin com.mediatek.wwtv.tvcenter com.mediatek.tvinput $PKG"
# Patterns for things the TV needs to work (TV/HDMI inputs, remote, keyboard, Bluetooth, updates of core modules).
CORE_REGEX='^com\.google\.android\.overlay\.modules|^com\.android\.providers\.|tvinput|tvcenter|inputmethod|\.ime$|keyboard|bluetooth|remote\.service|\.tv\.settings$|^com\.android\.server\.|^com\.android\.networkstack|^com\.google\.android\.networkstack|^com\.android\.tethering|^com\.google\.android\.tethering|^com\.android\.cellbroadcast|^com\.android\.wifi|^com\.google\.android\.wifi'
# Providers that are fine to disable despite the pattern above.
CORE_REGEX_EXCEPT='^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'

is_core() {
    local p="$1" x
    for x in $CORE_EXACT; do [ "$p" = "$x" ] && return 0; done
    if echo "$p" | grep -Eq "$CORE_REGEX"; then
        echo "$p" | grep -Eq "$CORE_REGEX_EXCEPT" && return 1
        return 0
    fi
    return 1
}

# Strip comments and blanks from a package list file.
read_list() { sed -e 's/#.*//' -e 's/[[:space:]]//g' "$1" | grep -v '^$' || true; }

screenshot() { # screenshot <out.png>
    local out="$1"
    adbx shell screencap -p /sdcard/tvhome-shot.png >/dev/null 2>&1 || true
    adbx pull /sdcard/tvhome-shot.png "$out" >/dev/null 2>&1 || true
    adbx shell rm -f /sdcard/tvhome-shot.png >/dev/null 2>&1 || true
    if [ ! -s "$out" ]; then
        echo "Screenshot is empty: Live TV, HDMI and some apps (Netflix) block screencap. That is the TV, not a fault." >&2
        echo "What is on screen: $(focused_app | head -1)" >&2
        return 1
    fi
    echo "Saved $out"
}

focused_app() { local l; l="$(tvsh dumpsys activity activities)"; echo "$l" | grep -E 'mFocusedApp|topResumedActivity|mResumedActivity' | head -3 || true; }

confirm() { # confirm "question" -> 0 on yes
    local a
    printf '%s [y/N] ' "$1"
    { read -r a </dev/tty; } 2>/dev/null || { echo; return 1; }
    case "$a" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}
