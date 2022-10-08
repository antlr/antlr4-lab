# syntax=docker/dockerfile:1

FROM ubuntu:latest

ARG LAB_VERSION=0.1-SNAPSHOT
ENV LAB_VERSION=${LAB_VERSION}

USER root:root

RUN apt-get update && \
    apt-get install --yes software-properties-common

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

ADD target/antlr4-lab-$LAB_VERSION-complete.jar antlr4-lab-$LAB_VERSION-complete.jar
ENTRYPOINT java -jar /app/antlr4-lab-$LAB_VERSION-complete.jar
EXPOSE 80
