FROM openjdk:17-jdk-slim
RUN apt-get update && apt-get install -y postgresql-client
WORKDIR /usr/local/bin
COPY target/*.jar app.jar
EXPOSE 8877
ENTRYPOINT ["java", "-Xms4G", "-Xmx8G", "-jar", "app.jar"]