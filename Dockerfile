FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system salon && useradd --system --gid salon --home-dir /app salon
WORKDIR /app
COPY --from=build --chown=salon:salon /build/target/salon-workforce-1.0.0.jar app.jar
USER salon
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]