# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Download dependencies first so they are cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run locally before each commit; skip them here to keep the image build fast.
RUN mvn -B -q package -DskipTests

# ---- Stage 2: run the jar on a small JRE image ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Do not run the app as root inside the container.
RUN addgroup -S leaveflow && adduser -S leaveflow -G leaveflow
USER leaveflow

COPY --from=build /app/target/leaveflow-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
