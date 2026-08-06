FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 avianto
COPY --from=build /app/target/avianto-back-*.jar app.jar
USER avianto
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
