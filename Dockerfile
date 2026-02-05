FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

COPY init-scripts/ep-jvm-tuning.sh /init-scripts/

COPY build/libs/eessi-pensjon-journalforing.jar /app/app.jar

ENV JAVA_OPTS="-Xms512m -Xmx3g"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]