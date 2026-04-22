FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
