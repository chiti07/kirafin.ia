#!/bin/sh
set -e

# Replace env vars in provisioning files before Grafana starts
if [ -f /etc/grafana/provisioning/datasources/prometheus.yml ]; then
    sed -e "s|\${PROMETHEUS_URL}|${PROMETHEUS_URL:-http://prometheus:9090}|g" \
        /etc/grafana/provisioning/datasources/prometheus.yml > /tmp/prometheus.yml
    mv /tmp/prometheus.yml /etc/grafana/provisioning/datasources/prometheus.yml
    echo "Generated datasource:"
    cat /etc/grafana/provisioning/datasources/prometheus.yml
fi

exec /run.sh
