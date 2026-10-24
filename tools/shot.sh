#!/usr/bin/env bash
# Press remote keys, then screenshot the TV.
#   tools/shot.sh shots/01-home.png                  (just capture)
#   tools/shot.sh shots/02-right.png DPAD_RIGHT DPAD_DOWN HOME
source "$(dirname "$0")/common.sh"
[ $# -ge 1 ] || { echo "Usage: tools/shot.sh <out.png> [KEY ...]"; exit 2; }
out="$1"; shift
for k in "$@"; do
    case "$k" in KEYCODE_*) ;; *) k="KEYCODE_$k" ;; esac
    adbx shell input keyevent "$k"
    sleep 0.6
done
sleep 1
mkdir -p "$(dirname "$out")"
screenshot "$out"
w="$(tvsh dumpsys window)"; echo "Focus: $(echo "$w" | grep mCurrentFocus | head -1)"
