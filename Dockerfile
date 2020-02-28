FROM navikt/java:8-appdynamics

COPY build/libs/eessi-pensjon-journalforing-*.jar /app/app.jar
COPY nais/export-vault-secrets.sh /init-scripts/

RUN chmod +x /init-scripts/*

ENV APPD_NAME eessi-pensjon
ENV APPD_TIER journalforing
#ENV APPD_ENABLED true
#ENV APPDYNAMICS_CONTROLLER_HOST_NAME appdynamics.adeo.no
#ENV APPDYNAMICS_AGENT_ACCOUNT_NAME NON-PROD
#ENV APPDYNAMICS_CONTROLLER_PORT 443
#ENV APPDYNAMICS_CONTROLLER_SSL_ENABLED true