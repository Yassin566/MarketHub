# MarketHub

Multi-vendor marketplace foundation using a modular monolith.

## Stack
- Java 21, Spring Boot 4.x, Maven
- PostgreSQL, Redis, Flyway
- Angular 21 frontend workspace bootstrap
- Docker Compose, Nginx, GitHub Actions

## Current implementation
The backend foundation includes customer identity, vendor lifecycle, vendor catalog and inventory, persistent carts and reservations, transactional order creation, payment request records, a lease-safe transactional outbox, and a deterministic sandbox payment provider. A signed sandbox webhook endpoint verifies HMAC signatures, deduplicates provider event IDs, and applies payment/order/inventory state changes transactionally. Late payment success after reservation expiry is held for review. Read-only administrative payment endpoints expose payment attempts and provider-event history for investigation. The worker and webhook endpoint are disabled until explicitly configured.

The sandbox provider and webhook are test infrastructure only: no production gateway, credentials, network calls, or real financial transactions are included. See `docs/` for API and architecture details.

This is an incremental implementation, not a claim that every marketplace capability is complete. Production gateway integration, provider-specific callbacks, reconciliation, refunds, taxes, shipping, commission/payout accounting, refresh-token rotation, and full Angular business screens remain future work.

## Run locally
```bash
cp .env.example .env
docker compose up -d
cd backend
mvn spring-boot:run
```

To exercise the sandbox dispatcher locally, set `MARKETHUB_PAYMENT_PROVIDER_MODE=sandbox` and `MARKETHUB_OUTBOX_WORKER_ENABLED=true`. To enable signed sandbox callbacks, set a strong `MARKETHUB_PAYMENT_WEBHOOK_SECRET`. Both are disabled unless configured.

API base: `http://localhost:8080/api/v1`; health: `http://localhost:8080/actuator/health`.

## CI
Pull requests targeting `main` run backend verification on Java 21 with PostgreSQL 17, including Flyway startup/migration validation and Hibernate schema validation. See [docs/ci.md](docs/ci.md).
