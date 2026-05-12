# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70"
ENV LOADER_MAIN=work.jscraft.alt.AltWebApplication

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -Dloader.main=${LOADER_MAIN} -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher"]
