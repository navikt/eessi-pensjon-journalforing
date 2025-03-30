FROM ghcr.io/navikt/baseimages/temurin:21

WORKDIR /app
RUN mkdir -p /data && chown -R 1000:1000 /data
COPY init-scripts/ep-jvm-tuning.sh /init-scripts/
VOLUME /data

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar
