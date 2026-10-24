#!/usr/bin/env bash
# Run one shell command on the TV:   tools/tv.sh pm list packages -d
source "$(dirname "$0")/common.sh"
[ $# -gt 0 ] || { echo "Usage: tools/tv.sh <command...>"; exit 2; }
tvsh "$@"
