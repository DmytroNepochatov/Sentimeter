#!/bin/sh
mvn clean package
docker-compose up --build -d
docker cp get_data_script.sh comments_analyzer:/usr/local/bin/
docker cp train_data.txt comments_analyzer:/usr/local/bin/