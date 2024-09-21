#!/bin/sh
mvn clean
docker rm -f comments_analyzer
docker rm -f translator
docker rmi -f comments-analyzer-image:1.0