FROM grafana/grafana:latest

ENV GF_SECURITY_ADMIN_USER=${GRAFANA_ADMIN_USER:-admin}
ENV GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-admin}
ENV GF_USERS_ALLOW_SIGN_UP=false

COPY provisioning/datasources /etc/grafana/provisioning/datasources
COPY provisioning/dashboards /etc/grafana/provisioning/dashboards

EXPOSE 3000
