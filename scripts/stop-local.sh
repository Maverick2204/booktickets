#!/bin/sh
# Tears down the containers started by run-local.sh.
docker rm -f bt-app bt-postgres bt-redis bt-rabbitmq
