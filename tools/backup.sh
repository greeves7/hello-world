#!/usr/bin/env bash
# Save everything restore.sh needs before anything is changed.  tools/backup.sh
source "$(dirname "$0")/common.sh"
need_tv

dir="$ROOT/backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$dir"
echo "Backing up to $dir"

tvsh pm list packages -f -u > "$dir/packages-all.txt"
tvsh pm list packages -e > "$dir/packages-enabled.txt"
tvsh pm list packages -d > "$dir/packages-disabled.txt"
tvsh pm list packages -s > "$dir/packages-system.txt"
tvsh pm list packages -3 > "$dir/packages-thirdparty.txt"
tvsh getprop > "$dir/getprop.txt"
for ns in global secure system; do tvsh settings list "$ns" > "$dir/settings-$ns.txt"; done
{
    echo "## resolve HOME"
    tvsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME || true
    echo "## HOME claimers"
    tvsh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME || true
} > "$dir/home.txt" 2>&1
{
    tvsh wm size
    tvsh wm density
    echo "display_density_forced=$(tvsh settings get secure display_density_forced)"
} > "$dir/display.txt"
{
    echo "usb: $(tvsh dumpsys usb | grep -o 'host_connected=[a-z]*' | head -1 || true)"
    echo "transport: $(tvsh dumpsys connectivity | grep -o 'Transports: [A-Z]*' | head -1 || true)"
    echo "wifi saved by: $(tvsh dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u | tr '\n' ' ' || true)"
    echo "private_dns_mode=$(tvsh settings get global private_dns_mode)"
    echo "private_dns_specifier=$(tvsh settings get global private_dns_specifier)"
} > "$dir/network.txt" 2>&1

# The first backup is the reference restore.sh --to-backup falls back to.
[ -e "$STATE/first-backup" ] || echo "$dir" > "$STATE/first-backup"
echo "Done: $(wc -l < "$dir/packages-all.txt" | tr -d ' ') packages, $(wc -l < "$dir/packages-disabled.txt" | tr -d ' ') already disabled."
cat "$dir/network.txt"
