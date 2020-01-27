FROM navikt/java:8

COPY build/libs/eessi-pensjon-journalforing-*.jar /app/app.jar

COPY export-vault-secrets.sh /init-scripts/
RUN chmod +x /init-scripts/*