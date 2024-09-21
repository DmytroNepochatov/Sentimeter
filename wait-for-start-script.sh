#!/bin/sh

SCRIPT_PATH="/usr/local/bin/get_data_script.sh"
TRAIN_DATA_PATH="/usr/local/bin/train_data.txt"

check_exist() {
  [ -f $SCRIPT_PATH ] && [ -f $TRAIN_DATA_PATH ]
}

while ! check_exist; do
  echo "Waiting on the configuration file to get the initial data and training dataset..."
  sleep 5
done

chmod +x $SCRIPT_PATH

if $SCRIPT_PATH; then
  echo "Script executed successfully, data fetched, starting application..."
  exec java -Xms4G -Xmx8G -jar /usr/local/bin/app.jar
else
  echo "Script execution failed, exiting..."
  exit 1
fi