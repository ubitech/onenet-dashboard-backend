FROM maven:3.6.3 AS maven

WORKDIR /usr/src/app
COPY . /usr/src/app
# Compile and package the application to an executable JAR
RUN mvn clean package

# For Java 11,
FROM adoptopenjdk/openjdk11:jre-11.0.9_11.1-alpine

ARG JAR_FILE=ONENET.jar

WORKDIR /opt/app

# Copy the spring-boot-api-tutorial.jar from the maven stage to the /opt/app directory of the current stage.
COPY --from=maven /usr/src/app/target/${JAR_FILE} /opt/app/

ENTRYPOINT ["java","-jar","${JAR_FILE}"]
EXPOSE 8080
