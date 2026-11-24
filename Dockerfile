FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG SKIP_TESTS=false
COPY pom.xml .
COPY src src
RUN --mount=type=cache,id=lec-blog-maven,target=/root/.m2/repository \
    sh -ceu 'if [ -n "$MAVEN_MIRROR_URL" ]; then \
      printf "<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>\n" "$MAVEN_MIRROR_URL" > /tmp/maven-settings.xml; \
      mvn -B -ntp -s /tmp/maven-settings.xml "-DskipTests=${SKIP_TESTS}" package; \
    else \
      mvn -B -ntp "-DskipTests=${SKIP_TESTS}" package; \
    fi'

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S blog && adduser -S -G blog blog
WORKDIR /app
COPY --from=build /build/target/lec-blog.jar app.jar
USER blog
ENV PORT=8080 TZ=UTC SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/healthz" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
