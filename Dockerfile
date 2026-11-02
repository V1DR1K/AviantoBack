FROM maven@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
WORKDIR /app
# pg_dump 17 (misma versión mayor que el servidor PostgreSQL) para las copias de seguridad desde la app.
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates \
 && install -d /usr/share/postgresql-common/pgdg \
 && wget -qO /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc \
 && . /etc/os-release \
 && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update && apt-get install -y --no-install-recommends postgresql-client-17 \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 avianto \
 && mkdir /backups && chown avianto /backups
COPY --from=build /app/target/avianto-back-*.jar app.jar
USER avianto
ENV BACKUP_DIR=/backups
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
