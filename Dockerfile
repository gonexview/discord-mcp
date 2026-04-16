FROM maven:3.9.6-amazoncorretto-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM amazoncorretto:17-alpine

WORKDIR /app

RUN adduser -D appuser

COPY --from=build --chown=appuser /app/target/*.jar app.jar

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:8085/actuator/health | grep -q '"status":"UP"' || exit 1

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
