FROM eclipse-temurin:21-jdk-jammy AS build

# Set the working directory
WORKDIR /workspace/app

# Copy the Maven wrapper and the pom.xml
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy the source code and build the application
COPY src src
RUN ./mvnw package -DskipTests

# Stage 2: Create the final, smaller image
FROM eclipse-temurin:21-jre-jammy

# Set an argument for the JAR file path
ARG JAR_FILE=/workspace/app/target/*.jar

# Copy the built JAR file from the 'build' stage to the final image
COPY --from=build ${JAR_FILE} app.jar

# Expose the port (this is documentation, the actual port is set at runtime)
# EXPOSE 8080

# Run the application
ENTRYPOINT ["java","-jar","/app.jar"]
