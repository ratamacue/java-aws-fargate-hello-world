FROM java:8-jdk

EXPOSE 8080
RUN mkdir app
WORKDIR /app

COPY target/hello-world-0.1-SNAPSHOT-jar-with-dependencies.jar /app/

ENTRYPOINT ["java", "-jar", "hello-world-0.1-SNAPSHOT-jar-with-dependencies.jar"]
