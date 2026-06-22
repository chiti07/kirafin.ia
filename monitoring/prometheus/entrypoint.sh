#!/bin/sh
set -e

HOST="${KIRA_LEDGER_HOST:-kira-ledger.railway.internal:8080}"
SCHEME="${KIRA_LEDGER_SCHEME:-https}"

# Strip scheme if the user accidentally included it
HOST=$(echo "$HOST" | sed -e 's|^https://||' -e 's|^http://||')

sed -i "s|__KIRA_LEDGER_TARGET__|$HOST|g" /etc/prometheus/prometheus.yml
sed -i "s|__KIRA_LEDGER_SCHEME__|$SCHEME|g" /etc/prometheus/prometheus.yml

cat /etc/prometheus/prometheus.yml

exec /bin/prometheus --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/prometheus
