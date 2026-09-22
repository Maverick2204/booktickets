# BookTickets

A concurrency-safe event ticket booking platform, built to demonstrate (and honestly measure)
this claim:

> Build a booking platform preventing overselling under concurrent load using PostgreSQL
> transactions and Redis-based distributed locks; processed async confirmations via
> RabbitMQ; deployed on AWS with Docker and CI/CD, sustaining ~1,198 req/s at 459ms p95
> under load testing.

Java 21 / Spring Boot 3, PostgreSQL, Redis (Redisson), RabbitMQ, Docker, Terraform, GitHub
Actions, k6.

## Architecture

```
                 ┌─────────────┐
  client ──────► │  Spring Boot │
                 │   REST API   │
                 └──────┬───────┘
                        │ 1. acquire per-event Redis lock (Redisson)
                        ▼
                 ┌─────────────┐
                 │  PostgreSQL  │  2. atomic UPDATE ... WHERE available_seats >= n
                 │ transaction  │     + insert Booking(PENDING)   <- correctness lives here
                 └──────┬───────┘
                        │ 3. release lock, commit
                        ▼
                 ┌─────────────┐  4. publish booking.created (after commit)
                 │  RabbitMQ    │
                 └──────┬───────┘
                        │ 5. async consumer
                        ▼
              simulate payment (PaymentGatewaySimulator)
                success -> booking CONFIRMED
                decline -> booking FAILED + inventory released back
                infra error -> retried, then dead-lettered (booking.confirmation.dlq)
```

### Why both a Redis lock *and* a Postgres transaction? (defense in depth)

- **The Redis distributed lock** (`BookingService`, via Redisson, one `RLock` per event) is a
  **throughput/coordination optimization**. It serializes concurrent booking attempts for the
  same event - useful once there's more than one app instance (see the ECS service running
  `desired_count = 2` in `infra/terraform`) - and reduces DB contention and wasted retries
  under load. It is deliberately **not** what guarantees correctness.
- **The Postgres transaction** (`BookingReservationWorker`) is what actually prevents
  overselling: a single atomic conditional update,
  `UPDATE events SET available_seats = available_seats - :n WHERE id = :id AND available_seats >= :n`,
  checked via affected-row-count, plus a `CHECK (available_seats >= 0)` constraint as the
  final backstop. This holds even if the lock is bypassed entirely, expires mid-request, or a
  node crashes holding it.
- This isn't just asserted in prose - `BookingConcurrencyWithoutRedisLockTest` reruns the
  exact same 200-concurrent-requests-for-50-seats scenario with the lock disabled
  (`booking.lock.enabled=false`, activating `NoOpLockService`) and gets the identical correct
  result. The lock changes throughput characteristics, not correctness.

## Project layout

```
src/main/java/com/bookings/booktickets/
  domain/        Event, Booking, BookingStatus
  repository/    EventRepository (atomic reserve/release), BookingRepository
  service/       BookingService (lock owner), BookingReservationWorker (@Transactional),
                 EventService, BookingConfirmationService, service/lock/ (lock abstraction)
  messaging/     RabbitMQ publisher/listener, BookingCreatedEvent, PaymentGatewaySimulator
  web/           REST controllers, DTOs, GlobalExceptionHandler
  config/        RedissonConfig, RabbitMqConfig (exchange/queue/DLQ topology)
src/main/resources/db/migration/   Flyway schema (events, bookings)
src/test/java/.../concurrency/     the centerpiece correctness tests
loadtest/k6/                       load test + SQL oversell verification
infra/terraform/                   AWS deployment as code (see below - not applied here)
.github/workflows/                 CI, Docker publish, gated AWS deploy
```

## Running locally

```bash
docker-compose up -d --build
```

This starts Postgres, Redis, RabbitMQ (management UI at http://localhost:15672, guest/guest),
and the app at http://localhost:8080. Flyway migrations run automatically on startup.

```bash
# Create an event
curl -X POST http://localhost:8080/api/events -H 'Content-Type: application/json' -d '{
  "name": "Concert", "venue": "Arena", "eventTime": "2027-01-01T20:00:00Z",
  "totalSeats": 100, "priceCents": 5000
}'

# Book seats (returns 201, status PENDING)
curl -X POST http://localhost:8080/api/bookings -H 'Content-Type: application/json' -d '{
  "eventId": 1, "userId": "alice", "seats": 2
}'

# Poll until the async consumer confirms it (PENDING -> CONFIRMED/FAILED)
curl http://localhost:8080/api/bookings/1
```

`userId` is a caller-supplied string only - this is a demo with no authentication.

## Testing

```bash
./mvnw verify
```

Runs unit tests (Surefire) and Testcontainers-backed integration tests (Failsafe), including:

- **`BookingConcurrencyTest`** - 200 truly concurrent booking attempts (Java 21 virtual
  threads, released simultaneously via a `CountDownLatch`) against 50 seats. Asserts exactly
  50 succeed, 150 are correctly rejected, and the database ends up consistent.
- **`BookingConcurrencyWithoutRedisLockTest`** - the same proof with the lock disabled,
  showing the database layer alone is sufficient.
- **`BookingControllerIT`** - end-to-end HTTP flow through the real broker, polling until a
  booking is confirmed.
- **`BookingConfirmationListenerIT`** - forces a business decline (asserts inventory is
  released back) and an infra failure (asserts the message is dead-lettered after retries).

All of the above were run against this exact stack; see `target/surefire-reports` and
`target/failsafe-reports` after running.

## Load test

```bash
docker-compose up -d
EVENT_ID=$(curl -s -X POST http://localhost:8080/api/events -H 'Content-Type: application/json' \
  -d '{"name":"Load Test Event","venue":"Load Test Hall","eventTime":"2027-01-01T10:00:00Z","totalSeats":500,"priceCents":0}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

k6 run loadtest/k6/booking-load-test.js -e BASE_URL=http://localhost:8080 -e EVENT_ID=$EVENT_ID

# then verify no overselling occurred:
docker-compose exec -T postgres psql -U booktickets -d booktickets -v event_id=$EVENT_ID < loadtest/verify-no-oversell.sql
```

### Real results (measured on this machine, single app instance, default pool sizes)

Ramping to 200 virtual users against a 500-seat event, deliberately requesting far more seats
in aggregate than available so the rejection path is genuinely exercised:

| Metric | Value |
|---|---|
| Total requests | 59,889 over 50s |
| **Throughput** | **~1,198 req/s** |
| **p95 latency** | **~459ms** |
| Median latency | ~67ms |
| Correctly handled (201/409/503) | 100% (59,889 / 59,889) |
| Overselling detected | **None** - `reserved_count` matched `total_seats - available_seats` exactly, both mid-run and after the async confirmation backlog drained |

These numbers are from a single container on a dev laptop with an untuned Hikari pool
(`maximum-pool-size: 20`) and default Tomcat thread settings - they're a honest floor, not a
tuned production ceiling. A real deployment (multiple ECS tasks behind the ALB, tuned
connection pools, a properly sized RDS instance) would be expected to do meaningfully better;
re-run the same script against that environment to get real numbers for it rather than
assuming these carry over.

## Docker

Multi-stage `Dockerfile` (Maven build stage -> `eclipse-temurin:21-jre-alpine` runtime,
non-root user, `/actuator/health` healthcheck). Verified end-to-end on this machine: image
builds, all four services start, the full booking -> async confirmation -> oversell-rejection
flow works through real HTTP calls against the containerized stack.

## CI/CD

- **`.github/workflows/ci.yml`** - `./mvnw verify` on every push/PR (Testcontainers-backed;
  GitHub-hosted runners have Docker natively).
- **`.github/workflows/docker-publish.yml`** - builds and pushes to GHCR on `main`/tags. Needs
  no extra secrets (`GITHUB_TOKEN` is sufficient), so it runs for real in any fork.
- **`.github/workflows/deploy.yml`** - deploys to the ECS service Terraform provisions below.
  Gated behind an `AWS_DEPLOY_ENABLED` repository variable so it cleanly **skips** (not fails)
  without AWS configured. Requires repo variables `AWS_REGION`, `ECS_CLUSTER_NAME`,
  `ECS_SERVICE_NAME` and secret `AWS_ROLE_ARN` (an IAM role trusting GitHub's OIDC provider).

## AWS infrastructure (Terraform)

`infra/terraform/` describes the target deployment: VPC with public/private subnets across 2
AZs, security groups scoped tightly (ALB -> ECS -> {RDS, Redis, MQ}, nothing else), RDS
Postgres, ElastiCache Redis (single node - see caveat below), Amazon MQ (RabbitMQ), an ALB,
and an ECS Fargate service running **`desired_count = 2`** - deliberately more than one
instance, since that's what makes the Redis distributed lock claim meaningful (a single
instance would never need cross-process coordination).

**This was validated with `terraform fmt` and `terraform validate` (both pass) and a
`terraform plan` that correctly proceeds through every provider-agnostic resource before
failing at the expected point - missing AWS credentials. It has not been `terraform apply`'d
in this session** - there's no AWS account configured here. Point it at a real account (`cd
infra/terraform && terraform init && terraform apply -var-file=envs/dev/dev.tfvars`) to stand
it up for real.

**Caveat:** the ElastiCache node here is a single instance. A true multi-master Redlock setup
(the algorithm's actual durability guarantee) needs several independent Redis nodes, which is
out of scope for this demo's cost budget - noted here rather than glossed over, since the
Postgres layer is what actually carries the correctness guarantee regardless.

## Design decisions worth knowing about

- **Atomic `UPDATE ... WHERE available_seats >= n`** instead of `SELECT ... FOR UPDATE` then
  check-then-update: single round trip, race-free via Postgres's implicit row lock during the
  update's scan/modify, no separate explicit lock statement needed. `SELECT ... FOR UPDATE` is
  the more commonly cited textbook approach; the conditional UPDATE is strictly simpler here
  and provides the same guarantee.
- **Single seat counter per event**, not per-seat rows: this is general-admission-style
  ticketing (quantity per booking), matching the resume claim without an unneeded seat map.
- **No authentication**: `userId` is a caller-supplied string, explicitly a demo
  simplification, not a security boundary.
- **Manual RabbitMQ ack/nack with a manual retry-count header**, capped at 3 attempts before
  dead-lettering, rather than Spring Retry - keeps the retry/DLQ topology fully visible in
  `RabbitMqConfig` rather than hidden in annotation config.
