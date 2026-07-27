# Droove

A ride-hailing platform built as an event-driven microservice system: Spring Boot services own transactional state (users, trips, matching, pricing, payments, scheduling), FastAPI services own high-concurrency sockets (GPS ingest, notification fanout), Kafka decouples the trip lifecycle, and Redis provides the geo index, scheduling queue, offer expiry, and rate limiting.

Full architecture, contracts (events, Redis keys, API bodies, ledger model) and the build plan live in [`Docs/plans/2026-07-14-droove-7day.md`](Docs/plans/2026-07-14-droove-7day.md). A rendered summary is in [`Docs/architecture.md`](Docs/architecture.md).

## Layout

```
Droove/
├─ Services/        Spring Boot + FastAPI microservices
├─ Frontends/        web (React) + mobile (Expo)
├─ Docker/           docker-compose.yml, init-db.sql
├─ Infra/terraform/  AWS deploy
├─ scripts/          smoke tests, e2e demo, GPS/traffic simulator
└─ Docs/             plans, contracts, interview prep
```

## Getting started

```bash
cp .env.example .env   # fill in real secrets locally, never commit .env
make up                 # postgres, redis, kafka (kraft), kafka-ui
make smoke               # curl-based smoke test against the running stack
```

Each service under `Services/*` is a standalone Maven or Python project — build/run it from its own directory (`./mvnw spring-boot:run`, `uvicorn app.main:app`, etc.).
