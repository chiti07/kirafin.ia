#!/bin/sh
set -e

HOST="${KIRA_LEDGER_HOST:-kira-ledger.railway.internal:8080}"
SCHEME="${KIRA_LEDGER_SCHEME:-https}"

# Strip scheme if the user accidentally included it
HOST=$(echo "$HOST" | sed -e 's|^https://||' -e 's|^http://||')

sed -e "s|__KIRA_LEDGER_TARGET__|$HOST|g" \
    -e "s|__KIRA_LEDGER_SCHEME__|$SCHEME|g" \
    /etc/prometheus/prometheus.yml > /tmp/prometheus.yml

echo "Generated prometheus.yml:"
cat /tmp/prometheus.yml

PORT="${PORT:-9090}"
echo "Listening on port $PORT"

exec /bin/prometheus --config.file=/tmp/prometheus.yml --storage.tsdb.path=/prometheus --web.listen-address=0.0.0.0:$PORT
