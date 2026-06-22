#!/bin/sh
set -e

# Replace env vars in provisioning files before Grafana starts
if [ -f /etc/grafana/provisioning/datasources/prometheus.yml ]; then
    sed -i "s|\${PROMETHEUS_URL}|${PROMETHEUS_URL:-http://prometheus:9090}|g" /etc/grafana/provisioning/datasources/prometheus.yml
fi

exec /run.sh
