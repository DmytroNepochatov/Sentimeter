#!/bin/sh
mvn clean package
docker-compose up --build -d
docker cp get_data_script.sh sentimeter:/usr/local/bin/
docker cp train_data.txt sentimeter:/usr/local/bin/