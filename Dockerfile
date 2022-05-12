FROM navikt/java:17-appdynamics

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar

ENV APPD_ENABLED true
ENV APPD_NAME eessi-pensjon
ENV APPD_TIER journalforing
