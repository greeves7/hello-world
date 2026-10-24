#!/usr/bin/env bash
# Build (or take a ready APK), install TV Home and make it the default Home.
#   tools/install-launcher.sh                    build with Gradle, then install
#   tools/install-launcher.sh path/to/app.apk    install an APK you downloaded from GitHub Actions
# The stock launcher stays enabled; disabling it is a separate step (tools/stock-launcher.sh).
source "$(dirname "$0")/common.sh"
need_tv

apk="${1:-}"
min="$(grep -Eo 'minSdk[[:space:]]+[0-9]+' "$ROOT/launcher/app/build.gradle" | grep -Eo '[0-9]+$')"
sdk="$(sdk_level)"
if [ "$sdk" -lt "$min" ]; then
    echo "This TV runs SDK $sdk; TV Home needs $min (Android 8.0). It cannot be installed here."
    echo "The debloat half still works; the stock launcher stays."
    exit 3
fi

if [ -z "$apk" ]; then
    if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ ! -f "$ROOT/launcher/local.properties" ]; then
        echo "No Android SDK found (ANDROID_HOME). Either install Android Studio / the command-line tools,"
        echo "or download the APK built by GitHub Actions (README, 'Get the APK') and run:"
        echo "  tools/install-launcher.sh ~/Downloads/app-debug.apk"
        exit 2
    fi
    (cd "$ROOT/launcher" && ./gradlew --no-daemon -q assembleDebug)
    apk="$ROOT/launcher/app/build/outputs/apk/debug/app-debug.apk"
fi
[ -s "$apk" ] || { echo "APK not found: $apk"; exit 2; }

echo "Installing $(basename "$apk")..."
# `adb install` can hang on Android 14 waiting for an on-screen prompt; push + pm install doesn't.
adbx push "$apk" /data/local/tmp/tvhome.apk >/dev/null
res="$(tvsh pm install -r /data/local/tmp/tvhome.apk 2>&1 || true)"
adbx shell rm -f /data/local/tmp/tvhome.apk >/dev/null 2>&1 || true
echo "$res"
case "$res" in
    *Success*) ;;
    *INSTALL_FAILED_VERIFICATION_FAILURE*)
        echo "This TV refuses sideloaded APKs. The launcher half cannot be done here; the debloat half still can."
        exit 3 ;;
    *INSTALL_FAILED_OLDER_SDK*)
        echo "The TV's Android is too old for this APK (minSdk $min)."; exit 3 ;;
    *)
        echo "Install failed. If it mentions HOME or a signature, this TV only accepts launchers signed by its maker."
        exit 3 ;;
esac

# Wi-Fi name in the top bar needs location permission (Android hides the SSID otherwise).
tvsh pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
tvsh pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true

current="$(tvsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
if [ ! -f "$STATE/stock-home.txt" ] && echo "$current" | grep -q / && ! echo "$current" | grep -q "$PKG"; then
    echo "$current" > "$STATE/stock-home.txt"
fi
echo "Apps that claim Home:"
tvsh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / | sed 's/^/  /'

echo "Setting TV Home as default Home..."
tvsh cmd package set-home-activity "$HOME_COMPONENT" || true
adbx shell input keyevent KEYCODE_HOME
sleep 3
f="$(focused_app)"
echo "$f"
screenshot "$SHOTS/launcher-home.png" || true
if echo "$f" | grep -q "$PKG"; then
    echo "OK: Home opens TV Home. The stock launcher is still enabled (that is fine)."
    echo "Optional next step: tools/stock-launcher.sh off"
else
    echo "Home still opens something else. set-home-activity can print Success and change nothing on"
    echo "Google TV builds, or the setup wizard's RecoveryActivity keeps the Home key."
    echo "Press Home on the remote: if Android asks which app to use, pick TV Home -> Always."
    echo "Otherwise: tools/stock-launcher.sh off  (it checks the setup wizard and Wi-Fi first)."
fi
