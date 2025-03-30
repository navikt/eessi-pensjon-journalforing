FROM ghcr.io/navikt/baseimages/temurin:21

COPY init-scripts/ep-jvm-tuning.sh /init-scripts/

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar
