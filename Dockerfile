FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S blog && adduser -S -G blog blog
WORKDIR /app
COPY --from=build /build/target/lec-blog.jar app.jar
USER blog
ENV PORT=8080 TZ=UTC SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/healthz" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
