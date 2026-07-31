# ============================================================================
# Étape 1 : build Maven multi-module
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY docuai-core/pom.xml               docuai-core/pom.xml
COPY docuai-security/pom.xml           docuai-security/pom.xml
COPY docuai-extraction-client/pom.xml  docuai-extraction-client/pom.xml
COPY docuai-ai-orchestration/pom.xml   docuai-ai-orchestration/pom.xml
COPY docuai-export/pom.xml             docuai-export/pom.xml
COPY docuai-api/pom.xml                docuai-api/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl docuai-api -am dependency:go-offline

COPY docuai-core               docuai-core
COPY docuai-security           docuai-security
COPY docuai-extraction-client  docuai-extraction-client
COPY docuai-ai-orchestration   docuai-ai-orchestration
COPY docuai-export             docuai-export
COPY docuai-api                docuai-api

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -pl docuai-api -am package && \
    cp docuai-api/target/docuai-api.jar /workspace/app.jar

# ============================================================================
# Étape 2 : runtime minimal
# ============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S docuai && adduser -S -G docuai docuai
COPY --from=build /workspace/app.jar app.jar
USER docuai

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
