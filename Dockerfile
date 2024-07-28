# syntax=docker/dockerfile:1

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

COPY src /app/src
COPY resources /app/resources
COPY static /app/static
COPY pom.xml /app/pom.xml

# Assumes mvn install was run prior to build Dockerfile
ADD target/antlr4-lab-$LAB_VERSION-complete.jar antlr4-lab-$LAB_VERSION-complete.jar
ENTRYPOINT java -jar /app/antlr4-lab-$LAB_VERSION-complete.jar
EXPOSE 80
