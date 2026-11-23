FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S -G spring spring
WORKDIR /home/spring/app

ARG JAR_FILE=build/libs/awgment-app-account-2.0.0-SNAPSHOT.jar
COPY --chown=spring:spring ${JAR_FILE} /home/spring/app/app.jar

USER spring:spring
EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=3s --start-period=25s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health/readiness | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/home/spring/app/app.jar"]
