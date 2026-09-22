#!/bin/sh
# Starts the full BookTickets stack (Postgres, Redis, RabbitMQ, app) via plain `docker run`
# on the host network. Use this instead of `docker-compose up` when docker-compose itself is
# broken (e.g. the old docker-compose 1.29.2 / podman / newer-requests incompatibility that
# throws "Not supported URL scheme http+docker"). Requires only the `docker` CLI (or a podman
# shim of it) to work - no docker-compose needed.
set -e

IMAGE_TAG="${IMAGE_TAG:-booktickets:local}"

echo "==> Building app image ($IMAGE_TAG)..."
docker build -t "$IMAGE_TAG" .

echo "==> Removing any previous containers..."
docker rm -f bt-postgres bt-redis bt-rabbitmq bt-app >/dev/null 2>&1 || true

echo "==> Starting postgres, redis, rabbitmq..."
docker run -d --name bt-postgres --network host \
  -e POSTGRES_DB=booktickets -e POSTGRES_USER=booktickets -e POSTGRES_PASSWORD=booktickets \
  postgres:16-alpine
docker run -d --name bt-redis --network host redis:7-alpine
docker run -d --name bt-rabbitmq --network host rabbitmq:3-management-alpine

echo "==> Waiting for postgres..."
for i in $(seq 1 30); do
  docker exec bt-postgres pg_isready -U booktickets -d booktickets >/dev/null 2>&1 && break
  sleep 1
done

echo "==> Waiting for redis..."
for i in $(seq 1 30); do
  docker exec bt-redis redis-cli ping 2>/dev/null | grep -q PONG && break
  sleep 1
done

echo "==> Waiting for rabbitmq..."
for i in $(seq 1 30); do
  docker exec bt-rabbitmq rabbitmq-diagnostics ping >/dev/null 2>&1 && break
  sleep 2
done

echo "==> Starting app..."
docker run -d --name bt-app --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/booktickets \
  -e SPRING_DATASOURCE_USERNAME=booktickets \
  -e SPRING_DATASOURCE_PASSWORD=booktickets \
  -e SPRING_DATA_REDIS_HOST=localhost \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_RABBITMQ_HOST=localhost \
  -e SPRING_RABBITMQ_PORT=5672 \
  -e SPRING_RABBITMQ_USERNAME=guest \
  -e SPRING_RABBITMQ_PASSWORD=guest \
  "$IMAGE_TAG"

echo "==> Waiting for app to become healthy..."
for i in $(seq 1 30); do
  curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1 && break
  sleep 2
done

echo "==> Up. App: http://localhost:8080  RabbitMQ UI: http://localhost:15672 (guest/guest)"
curl -s http://localhost:8080/actuator/health; echo
