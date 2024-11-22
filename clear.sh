#!/bin/sh
mvn clean
docker rm -f sentimeter
docker rm -f translator
docker rmi -f sentimeter-image:1.0