# syntax=docker/dockerfile:1

FROM ubuntu:latest
#FROM maven:3.8.3-openjdk-17 AS build

# Install basic software support
RUN apt-get update && \
    apt-get install --yes software-properties-common


USER root:root

WORKDIR /app

RUN apt update
RUN apt install -y ghostscript pdf2svg texlive-extra-utils

RUN apt install -y openjdk-11-jre
RUN apt install -y maven
RUN apt install -y git

COPY src /app/src
COPY resources /app/resources
COPY static /app/static
COPY pom.xml /app/pom.xml

RUN ls -la /app/*

ADD target/antlr4-lab-0.1-SNAPSHOT-complete.jar antlr4-lab-0.1-SNAPSHOT-complete.jar
ENTRYPOINT ["java","-jar","/app/antlr4-lab-0.1-SNAPSHOT-complete.jar"]
EXPOSE 80

# ---------------

#RUN mvn dependency:resolve
RUN mvn -Dmaven.repo.local=/app install
#RUN mvn install

#FROM gcr.io/distroless/java
#COPY --from=build /app/target/antlr4-lab-0.1-SNAPSHOT-complete.jar /app/antlr4-lab-0.1-SNAPSHOT-complete.jar

#COPY /app/target/antlr4-lab-0.1-SNAPSHOT-complete.jar /app
#COPY /app/org/antlr/antlr4-lab/0.1-SNAPSHOT/antlr4-lab-0.1-SNAPSHOT-complete.jar /app
#COPY target/antlr4-lab-0.1-SNAPSHOT-complete.jar /app
#EXPOSE 80

#RUN "cp", "target/antlr4-lab-0.1-SNAPSHOT-complete.jar", "/app"]
#CMD ["java", "-cp", "/app/org/antlr/antlr4-lab/0.1-SNAPSHOT/antlr4-lab-0.1-SNAPSHOT.jar", "org.antlr.v4.server.ANTLRHttpServer"]
#CMD ["java", "-cp", "/app/antlr4-lab-0.1-SNAPSHOT-complete.jar", "org.antlr.v4.server.ANTLRHttpServer"]
#mvn compile exec:java -Dexec.mainClass="com.baeldung.main.Exec"

