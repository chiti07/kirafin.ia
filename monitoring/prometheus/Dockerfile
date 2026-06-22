FROM prom/prometheus:latest

COPY prometheus.yml /etc/prometheus/prometheus.yml
COPY entrypoint.sh /entrypoint.sh

USER root
RUN chmod +x /entrypoint.sh && chmod 666 /etc/prometheus/prometheus.yml
USER nobody

EXPOSE 9090

ENTRYPOINT ["/entrypoint.sh"]
