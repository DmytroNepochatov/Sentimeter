FROM openjdk:17-jdk-slim
RUN apt-get update && apt-get install -y postgresql-client
WORKDIR /usr/local/bin
COPY target/*.jar app.jar
COPY wait-for-start-script.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/wait-for-start-script.sh
EXPOSE 8877
ENTRYPOINT ["/usr/local/bin/wait-for-start-script.sh"]