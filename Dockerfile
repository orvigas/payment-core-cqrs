# Build stage
FROM maven:3-amazoncorretto-23-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:23-alpine

WORKDIR /app

COPY --from=builder /build/target/payment-core-*.jar /app/payment-core.jar

EXPOSE 8080 5005

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

CMD ["java", "-jar", "/app/payment-core.jar", \
     "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]
