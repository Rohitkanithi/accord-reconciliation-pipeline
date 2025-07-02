# Stage 1: Build the application using Maven
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

# Stage 2: Create the final, lightweight container image
FROM openjdk:17-jdk-slim
WORKDIR /app
# Change this line for verification-service
COPY --from=build /app/target/verification-service-0.0.1-SNAPSHOT.jar ./app.jar
# Change the EXPOSE port
EXPOSE 8081
# The command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]