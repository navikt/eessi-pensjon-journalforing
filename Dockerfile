FROM ghcr.io/navikt/baseimages/temurin:21

WORKDIR /app
RUN mkdir -p  /app/data
RUN chmod a+w /app/data
COPY init-scripts/ep-jvm-tuning.sh /init-scripts/
VOLUME /data

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar
