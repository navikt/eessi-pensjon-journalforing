FROM navikt/java:17

COPY init-scripts/ep-jvm-tuning.sh /init-scripts/

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar
