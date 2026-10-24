#!/usr/bin/env bash
# Sort the installed packages into review lists. Disables nothing.
#   tools/classify.sh            -> lists/review.txt
# Then copy the lines you want into a batch file, remove their leading '#', and run disable.sh.
source "$(dirname "$0")/common.sh"
need_tv

out="$ROOT/lists/review.txt"
all="$(tvsh pm list packages -f -u | sed 's/^package://')"

STREAMING='netflix|youtube|amazon|primevideo|disney|hulu|plex|spotify|hbo|\.max\.|paramount|peacock|twitch|crunchyroll|apple\.atve|dazn|tubi|pluto|vudu|showtime|starz|deezer|tidal|kodi|vlc|jellyfin|emby|mubi|sling|fubo|britbox|bbc|itv|channel4|stan\.|binge|kayo|foxtel|hotstar|zee5|sonyliv'
ASK='katniss|assistant|voice|mediashell|cast|miracast|wfd|update|\.ota|fota|upgrade|tvrecommendations'
FACTORY='factory|\.fac\.|\.fac$|hkcfac|\.at\.factory|production|factorymenu|aging|burnin'
TELEMETRY='analytic|telemetry|statist|report|feedback|logcollect|bigdata|crash|tracker|diagnos|dataservice|survey|\.log$|logger|usagestat|beacon|\.dmp|collect'
CONTENT='\.ads?\.|\.ads?$|advert|recommend|promo|content|\.vod|discover|feature|guide|dreamx|backdrop|ambient|partner|store|market|shop|news|weather|sponsor|hisense\.(home|smartcast|vidaa)|patchwall'
BACKGROUND='share|dlna|mirror|airplay|cleaner|clean|boost|optimi|accelerat|\.demo|retail|showcase|tips|tutorial|manual|help'
LAUNCHER='launcher|setupwraith'

tagged="$(echo "$all" | while IFS= read -r line; do
    [ -n "$line" ] || continue
    path="${line%=*}"; p="${line##*=}"
    if is_core "$p"; then c=KEEP
    elif echo "$p" | grep -Eiq "$LAUNCHER"; then c=LAUNCHER
    elif echo "$p" | grep -Eiq "$FACTORY"; then c=FACTORY
    elif echo "$p" | grep -Eiq "$STREAMING"; then c=STREAMING
    elif echo "$p" | grep -Eiq "$ASK"; then c=ASK
    elif echo "$p" | grep -Eiq "$TELEMETRY"; then c=TELEMETRY
    elif echo "$p" | grep -Eiq "$CONTENT"; then c=CONTENT
    elif echo "$p" | grep -Eiq "$BACKGROUND"; then c=BACKGROUND
    else c=OTHER; fi
    loc=user; case "$path" in /system/*|/product/*|/vendor/*|/system_ext/*|/odm/*|/oem/*) loc=system;; esac
    printf '%s %s %s %s\n' "$c" "$p" "$loc" "$path"
done | sort -k2)"

{
echo "# Review list for $(tvsh getprop ro.product.manufacturer) $(tvsh getprop ro.product.model), Android $(tvsh getprop ro.build.version.release), $(date +%Y-%m-%d)"
echo "# Every line starts commented out. Check unknown ones with tools/whatis.sh <pkg> first."
echo "# Copy what you want into a batch file (lists/batch1.txt ...), remove the leading '#', then:"
echo "#   tools/disable.sh lists/batch1.txt"
echo "# Never uncomment anything in the KEEP section; disable.sh refuses those anyway."
for section in TELEMETRY CONTENT BACKGROUND ASK STREAMING LAUNCHER FACTORY OTHER KEEP; do
    echo
    case $section in
        TELEMETRY)  echo "## Manufacturer telemetry / analytics / feedback (usually safe to disable)";;
        CONTENT)    echo "## Content, ads and recommendation services (usually safe; check with whatis.sh)";;
        BACKGROUND) echo "## Background extras: share/DLNA duplicates, cleaners, demo/retail modes";;
        ASK)        echo "## Ask first: voice assistant, Chromecast, Miracast, update services";;
        STREAMING)  echo "## Streaming apps: keep the ones you use";;
        LAUNCHER)   echo "## Stock launcher / setup wizard: handled by tools/stock-launcher.sh, not here";;
        FACTORY)    echo "## Factory / production tools: do NOT launch these; leaving them alone is fine";;
        OTHER)      echo "## Everything else (mostly system). Leave unless you know what it is";;
        KEEP)       echo "## KEEP: needed for the TV to work or boot (refused by disable.sh)";;
    esac
    echo "$tagged" | awk -v s="$section" '$1 == s { printf "#%-60s # %s %s\n", $2, $3, $4 }'
done
} > "$out"
echo "Wrote $out"
grep -c '^#[a-z]' "$out" | xargs echo "packages listed:"
