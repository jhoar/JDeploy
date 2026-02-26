# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY backend-api/pom.xml backend-api/pom.xml
COPY vaadin-ui/pom.xml vaadin-ui/pom.xml
COPY backend-api/src backend-api/src

RUN mvn -pl backend-api -am -DskipTests package
RUN find /workspace/backend-api/target -maxdepth 1 -type f -name 'backend-api-*.jar' ! -name '*.original' -exec cp {} /workspace/backend-api/target/app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/backend-api/target/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
