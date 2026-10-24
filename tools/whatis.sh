#!/usr/bin/env bash
# Show what a package is without starting it:   tools/whatis.sh com.example.pkg
# Never "just launch" a factory/production package to see what it does (README, Debloat).
source "$(dirname "$0")/common.sh"
p="${1:?Usage: tools/whatis.sh <package>}"
d="$(tvsh dumpsys package "$p")"
echo "$d" | grep -E 'versionName|codePath=|pkgFlags=|enabled=' | head -8 || true
echo "-- components:"
echo "$d" | grep -oE "$p/[A-Za-z0-9_.\$]+" | sort -u | head -60 || true
echo "-- intent actions it handles:"
echo "$d" | grep -oE 'Action: "[^"]+"' | sort -u | head -30 || true
