# ---- Build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies are cached separately from the source so code edits do not
# trigger a full re-download.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system education \
    && useradd --system --gid education --home /app education

COPY --from=build /build/target/education-*.jar /app/app.jar

# /app/config/application.yml, when mounted, overrides the built-in client settings.
# The database is MySQL; Flyway migrates it on start-up.
RUN mkdir -p /app/config && chown -R education:education /app

USER education
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
