#!/usr/bin/env bash
# Optional ad-blocking DNS through Android's Private DNS (Android 9+).
#   tools/dns.sh on [hostname]     default dns.adguard-dns.com
#   tools/dns.sh off               back to what it was before (or opportunistic)
#   tools/dns.sh status
# It does NOT block YouTube ads. If your router filters by domain (Pi-hole, AdGuard Home, a VPN
# with domain lists, a fake-IP proxy), Private DNS goes around it: block on the router instead.
source "$(dirname "$0")/common.sh"
need_tv
[ "$(sdk_level)" -ge 28 ] || { echo "Private DNS needs Android 9 or newer."; exit 3; }

case "${1:-status}" in
    on)
        host="${2:-dns.adguard-dns.com}"
        confirm "Does your router already filter or route traffic by domain (Pi-hole, AdGuard Home, VPN rules)? Answer y to stop" && {
            echo "Then leave Private DNS off and block on the router. Nothing changed."; exit 0; }
        if [ ! -f "$STATE/dns-prev.txt" ]; then
            echo "prev_mode=$(tvsh settings get global private_dns_mode)" > "$STATE/dns-prev.txt"
            echo "prev_spec=$(tvsh settings get global private_dns_specifier)" >> "$STATE/dns-prev.txt"
        fi
        tvsh settings put global private_dns_specifier "$host"
        tvsh settings put global private_dns_mode hostname
        echo "Private DNS: $host. Open a streaming app and check it still plays." ;;
    off)
        prev_mode=opportunistic; prev_spec=""
        # shellcheck disable=SC1091
        [ -f "$STATE/dns-prev.txt" ] && . "$STATE/dns-prev.txt"
        [ "$prev_mode" = "null" ] && prev_mode=opportunistic
        tvsh settings put global private_dns_mode "$prev_mode"
        rm -f "$STATE/dns-prev.txt"
        echo "Private DNS: $prev_mode" ;;
    status)
        echo "mode=$(tvsh settings get global private_dns_mode) specifier=$(tvsh settings get global private_dns_specifier)" ;;
    *) echo "Usage: tools/dns.sh on [host]|off|status"; exit 2 ;;
esac
