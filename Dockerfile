# syntax=docker/dockerfile:1
FROM --platform=linux/x86_64 maven:3.9.5-eclipse-temurin-11-alpine as builder

WORKDIR /tmp

COPY src /tmp/src
COPY resources /tmp/resources
COPY static /tmp/static
COPY pom.xml /tmp/pom.xml

RUN mvn -B -f /tmp/pom.xml clean package

FROM ubuntu:latest

ARG LAB_VERSION=0.4-SNAPSHOT
ENV LAB_VERSION=${LAB_VERSION}

USER root:root

WORKDIR /app

RUN apt-get update && \
    apt-get install --yes software-properties-common

RUN apt update
RUN apt install -y ghostscript pdf2svg texlive-extra-utils

RUN apt install -y openjdk-11-jre

COPY resources /app/resources
COPY static /app/static

# Assumes mvn install was run prior to build Dockerfile
COPY --from=builder /tmp/target/antlr4-lab-$LAB_VERSION-complete.jar antlr4-lab-$LAB_VERSION-complete.jar
ENTRYPOINT java -jar /app/antlr4-lab-$LAB_VERSION-complete.jar
EXPOSE 80
