# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy build descriptor, wrapper, and multi-module poms
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY digital-svc-model/pom.xml digital-svc-model/
COPY digital-svc-main/pom.xml digital-svc-main/
COPY digital-svc-acceptance/pom.xml digital-svc-acceptance/
COPY digital-svc-integration/pom.xml digital-svc-integration/
COPY digital-svc-end2end/pom.xml digital-svc-end2end/
COPY digital-svc-aft/pom.xml digital-svc-aft/
RUN chmod +x mvnw
RUN ./mvnw -pl digital-svc-main -am dependency:go-offline -B

# Copy source code and package application
COPY digital-svc-model/src digital-svc-model/src
COPY digital-svc-main/src digital-svc-main/src
RUN ./mvnw -pl digital-svc-main -am clean package -DskipTests -B && \
    cp digital-svc-main/target/digital-svc-main-*-exec.jar /workspace/app.jar

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S banking && adduser -S banking -G banking
USER banking:banking

COPY --from=builder /workspace/app.jar app.jar

ENV SERVER_PORT=8081
ENV SPRING_PROFILES_ACTIVE=demo
EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
