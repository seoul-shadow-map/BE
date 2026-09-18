#!/bin/sh
# WSL systemd services alone do not keep the distribution running.
set -eu
if [ "${1:-}" = stop ]; then
    if [ -f /run/shadow-map-db-keepalive.pid ]; then
        keeper_pid=$(cat /run/shadow-map-db-keepalive.pid)
        keeper_command=$(tr '\000' ' ' < "/proc/$keeper_pid/cmdline" 2>/dev/null || true)
        case "$keeper_command" in
            *'/database/keepalive.sh'*) kill "$keeper_pid" ;;
        esac
    fi
    exit 0
fi
exec 9>/run/shadow-map-db-keepalive.lock
flock -n 9 || exit 0
echo $$ > /run/shadow-map-db-keepalive.pid
trap 'rm -f /run/shadow-map-db-keepalive.pid; exit 0' TERM INT EXIT
while true; do sleep 30 & wait $!; done
