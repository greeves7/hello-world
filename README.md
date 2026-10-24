# TV Home: debloat an Android TV and give it a clean home screen

This repo debloats an Android TV / Google TV over ADB and replaces the stock launcher with **TV
Home**: a small launcher in the style of Apple TV. It has a pitch-black background, a dock of your
favourite apps, a grid of the rest, one glass highlight that moves between tiles, and a clock top
left.

Written for a **Hisense "SmartTV 4K", Android TV 11 (build `RTT2.220118.001`, SDK 30)**. It
should work on most Android TV / Google TV sets running Android 8.0 or newer. It does not work on
Samsung Tizen, LG webOS or Fire TV.

> **Status:** the launcher compiles and GitHub Actions builds the APK. The scripts have been
> tested against a simulated TV, **not yet on your TV**. Go one step at a time and look at the
> screen after each one. Everything here can be undone (`tools/restore.sh`).

```
launcher/          Android project for TV Home (Java, framework views only, no libraries)
tools/             Bash scripts that drive the TV over ADB (macOS and Linux)
lists/             package lists: a starter list for Hisense, and review.txt once you run classify.sh
state/ backup/     written by the scripts: what was changed, and the original settings. Keep these.
```

## What you need

- A computer on the **same network as the TV**, with `adb` (macOS: `brew install
  android-platform-tools`, Ubuntu/Debian: `sudo apt install adb`) and `python3`.
- The launcher APK. Either:
  - **Download it:** GitHub → this repo → **Actions** → *Build launcher* → the latest run →
    **Artifacts** → `tvhome-debug-apk` (a zip that contains `app-debug.apk`), or
  - **Build it** yourself: JDK 17 and the Android SDK (Android Studio installs both). Set
    `ANDROID_HOME`, and `tools/install-launcher.sh` builds the APK for you.

## Before you start, on the TV

1. Settings → Device Preferences → About → press OK on **Build** 7 times.
2. Back → Developer options → turn on **USB debugging** (and **Network debugging** / **ADB
   debugging** if your TV shows it).
3. **USB warning:** some TVs cut power to their USB ports while USB debugging is on. A USB drive,
   speaker or **USB Ethernet adapter** plugged in can go dead until debugging is off again. If
   the TV's network comes through a USB Ethernet adapter, do the whole job in one sitting.
4. Find the TV's IP: Settings → Network & Internet → your network.

## Step by step

```bash
tools/connect.sh 192.168.1.50      # accept "Allow USB debugging?" on the TV (tick Always allow)
tools/backup.sh                    # saves packages, settings, getprop, display and network state
tools/classify.sh                  # writes lists/review.txt, sorted into categories; disables nothing
```

**1. Debloat in small batches.** Open `lists/review.txt` and `lists/hisense-google-tv.txt`.
Before you disable a package whose name doesn't say what it does, check it with
`tools/whatis.sh <package>`. That reads its activities and services **without starting it**.
Never *launch* a factory/production package to find out what it is: on some boards that alone
breaks the Input key and CEC. Put the packages you choose into `lists/batch1.txt`,
`lists/batch2.txt` and so on, a few at a time, without the leading `#`:

```bash
tools/disable.sh lists/batch1.txt  # 5 at a time; screenshot + pause after each batch; "u" undoes it
tools/reboot-check.sh              # after the last batch: reboot, see what the firmware re-enabled
```

`disable.sh` only ever runs `pm disable-user --user 0`. It never uninstalls anything, and it runs
one `pm` command per shell call. It refuses a whole list if any entry is something the TV needs
(see *Never touched* below). It stops if the TV refuses every package. That firmware can't be
debloated, so use DNS blocking instead.

**2. Install TV Home.**

```bash
tools/install-launcher.sh ~/Downloads/app-debug.apk   # or with no argument, to build it
```

This installs with `pm install -r`, which avoids the Android 14 `adb install` hang. It grants the
location permission Android needs before it shows the Wi-Fi network name, then makes TV Home the
default Home. It checks the result with `dumpsys activity`, because on Google TV
`set-home-activity` can print `Success` and change nothing. The stock launcher stays enabled.

**3. Optional: hand Home over for good.**

```bash
tools/stock-launcher.sh off        # disables the stock launcher; asks before each one
```

On Google TV the setup wizard (`com.google.android.tungsten.setupwraith`) can take Home after
the stock launcher is gone. Before it disables the wizard, the script checks whether your saved
Wi-Fi network was created by it. Android deletes that network when the wizard is disabled, which
takes the TV off Wi-Fi and ADB. If the check finds this, the script stops and asks you to forget
and re-join the network first. If Home isn't TV Home at the end, it puts the stock launcher back
automatically. `tools/stock-launcher.sh on` undoes it.

**4. Test the remote's buttons.** The Google TV launcher also answers the app buttons (Netflix,
YouTube...), Input and the Settings/dashboard button. Press each one. See *Remote buttons* for
any that stop working.

**5. Optional: ad-blocking DNS** (Android 9+):

```bash
tools/dns.sh on                    # Private DNS = dns.adguard-dns.com
tools/dns.sh off                   # undo (back to what it was, normally "opportunistic")
```

It does **not** block YouTube ads. Check that a streaming app still plays afterwards. If your
router already filters by domain (Pi-hole, AdGuard Home, a VPN with domain lists, a fake-IP
proxy), skip this: Private DNS goes around the router, and blocking there is the better choice.

**6. Close ADB when you're done.**

```bash
tools/finish.sh                    # walks you through it, then checks the port really refuses
```

Switch off USB debugging and Network debugging, revoke the USB debugging authorisations, and
restart the TV. If the TV's network is a USB Ethernet adapter, unplug and replug it afterwards,
and check that Settings shows Ethernet, not Wi-Fi. Port 5555 is plaintext; on firmware with
`ro.adb.secure=0` anyone on your network can get a shell while it is open. Only "connection
refused" from `tools/adb-closed.py <ip>` proves the port is closed. On macOS, `nc` can't prove it.

Take screenshots any time: `tools/shot.sh shots/01.png DPAD_RIGHT` presses the key, then pulls a
screenshot. With no key, it captures what is on screen. Live TV, HDMI and Netflix give empty
screenshots. That is the TV protecting the picture, not a fault.

## Using TV Home

- **D-pad:** Up/Down move exactly one row. Up from the dock goes to the top bar (Inputs, Wi-Fi,
  Settings), and the ends of each row stop instead of wrapping. Down from the dock scrolls the
  grid up. **Back** returns to the dock. It remembers the last tile you used.
- **Long-press OK** on a tile opens that app's *App info*, where you can disable, clear data or
  uninstall it.
- **Inputs** opens a dropdown with the HDMI ports, AV and Live TV, switching through Android's
  TV input framework.
- It stays light: no blur, no video, no polling. The clock and network pill update from system
  events.

### Change the dock, columns or wallpaper

Everything is in `launcher/app/src/main/res/values/config.xml`:

```xml
<integer name="columns">4</integer>             <!-- 4, 5, 6 or 7 tiles per row -->
<string-array name="dock_apps">                 <!-- left to right, by package name -->
    <item>com.google.android.youtube.tv</item>
    <item>com.netflix.ninja</item>
    ...
</string-array>
<string-array name="hidden_apps"> ... </string-array>
```

Package names: `tools/tv.sh pm list packages -3` (apps you installed) or `tools/tv.sh pm list
packages`.

**Wallpaper:** put a `wallpaper.jpg` (or `.png`, around 1920×1080) into
`launcher/app/src/main/res/drawable-nodpi/`, and set `wallpaper_dim_percent` if it's too bright.
Delete the file to go back to black.

After any change, rebuild and reinstall: push the change and use the new GitHub Actions APK, or
run `tools/install-launcher.sh`.

The typeface is [Outfit](https://github.com/Outfitio/Outfit-Fonts) (SIL Open Font License,
`launcher/FONT-LICENSE-OFL.txt`).

## Remote buttons

If a button stops working once the stock launcher is off, TV Home has an **optional**
accessibility helper that maps it again:

- `input_key_keycodes` (default 178, Input) opens the Inputs menu.
- `settings_key_keycodes` (default 176) and `settings_key_scancodes` open Settings.
- `app_keys`, as `keycode=package`, launches an app.

```bash
tools/keys.sh scan                 # press the button: shows its key code / scan code
tools/keys.sh on                   # enable the helper (asks you to test holding Volume Up first)
tools/keys.sh off
```

A button that shows up as `KEY_UNKNOWN` or `KEY_PROGRAM` shares its key code with every other
unmapped button, so map it by scan code (`settings_key_scancodes`).

**Trade-off:** on some firmware (measured on a TCL Google TV 12 build), *any* key-filtering
service makes held buttons repeat once per press, so a held volume button steps once instead of
ramping. Hold Volume Up before and after you enable the helper. If it stopped repeating, you are
choosing between that and the remapped buttons. Your remote isn't broken. Test with the real
buttons: a key injected over ADB doesn't prove the helper works.

## Undo

```bash
tools/restore.sh                   # re-enable everything we disabled, put Home and Private DNS back
tools/restore.sh --to-backup       # also re-enable anything disabled since the first backup
tools/restore.sh com.example.app   # re-enable one app
tools/tv.sh pm uninstall io.github.greeves7.tvhome   # remove TV Home itself
```

Without a computer: Settings → Apps → See all apps → Show system apps → pick the app → **Enable**.

## Never touched

`disable.sh` refuses a list that contains any of these:

- **What the TV needs to work:** the TV app and inputs (`com.mediatek.wwtv.tvcenter`,
  `com.mediatek.tvinput`), the remote and Bluetooth services, the on-screen keyboard, Play Store,
  Play services, Settings (`com.android.tv.settings`).
- **Android's core packages,** without which the TV may not boot: the permission controller,
  package installer, ext services/shared, module metadata (as `com.android.*` and
  `com.google.android.*`), `com.google.android.overlay.modules*`, `com.android.providers.*`
  (calendar, contacts, user dictionary and blocked numbers aside), `com.android.systemui`,
  `com.android.shell`, `com.android.phone`, `com.android.settings` and `android`.

## Troubleshooting

- **macOS: `adb` says "No route to host" but the port is open.** macOS Local Network privacy is
  blocking adb. Allow your terminal under System Settings → Privacy & Security → Local Network,
  or use the Python [`adb-shell`](https://pypi.org/project/adb-shell/) package in a venv as the
  client.
- **Port 5555 refused.** Google TV sets often only offer *Wireless debugging*. Go to Developer
  options → Wireless debugging → Pair device with pairing code, then run `adb pair <ip>:<pair
  port>`, `adb connect <ip>:<port>`, and `adb tcpip 5555`. Android switches Wireless debugging off
  whenever Wi-Fi drops. Some firmware uses another fixed port: `TV_PORT=5877 tools/connect.sh <ip>`.
- **Screen looks enlarged or clipped.** Before you blame the launcher, check `tools/tv.sh wm
  size`, `tools/tv.sh wm density` and `tools/tv.sh settings get secure display_density_forced`
  (empty is the normal default). `backup/*/display.txt` has the original values.
- **Screenshots keep showing an app you left.** `screencap` is returning its last frame. Check
  `tools/tv.sh dumpsys window | grep mCurrentFocus`, force-stop that app, then capture again.
- **Black screen on Home after a reboot.** Run `tools/stock-launcher.sh on`. Then check `tools/tv.sh
  logcat -d | grep RecoveryActivity` for the setup wizard.
- **The app grid is empty.** On Android 11+ that means package visibility. TV Home declares the
  `<queries>` it needs, so check `tools/tv.sh pm list packages` first.
