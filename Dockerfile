FROM ghcr.io/navikt/baseimages/temurin:21

WORKDIR /app
RUN mkdir -p  /app/data
WORKDIR /app/data
RUN touch /app/data/journalpostIderSomGikkBra.txt
RUN chmod -R 777 /app/data/journalpostIderSomGikkBra.txt
WORKDIR /app
COPY init-scripts/ep-jvm-tuning.sh /init-scripts/
VOLUME /data

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar
