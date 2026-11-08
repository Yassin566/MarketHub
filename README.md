# MarketHub

Multi-vendor marketplace foundation using a modular monolith.

## Stack
- Java 21, Spring Boot 4.x, Maven
- PostgreSQL, Redis, Flyway
- Angular 21 frontend workspace bootstrap
- Docker Compose, Nginx, GitHub Actions

## Current implementation
The backend includes customer identity, vendor lifecycle, vendor catalog and inventory, persistent carts and reservations, transactional order creation, payment request records, a lease-safe transactional outbox, and a deterministic sandbox payment provider. Signed sandbox webhooks verify HMAC signatures, deduplicate provider event IDs, and apply payment/order/inventory changes transactionally. Admin-only read APIs expose payment attempts and provider-event history.

An optional stale-payment review worker detects payments stuck in PROCESSING and flags them for manual review. Admins can record an audited, evidence-backed resolution after independently verifying provider status. A confirmed success consumes stock only while reservations remain active and unexpired; otherwise the payment stays under review. A confirmed failure releases active reservations and cancels a still-pending order.

The reconciliation worker, outbox worker, and webhook endpoint are disabled unless explicitly configured. The sandbox provider and webhook are test infrastructure only: no production gateway, credentials, network calls, or real financial transactions are included. See docs/ for API and architecture details.

This is an incremental implementation. Production gateway integration, authoritative provider reconciliation, refunds, taxes, shipping, commission/payout accounting, refresh-token rotation, and full Angular business screens remain future work.

## Run locally
```bash
cp .env.example .env
docker compose up -d
cd backend
mvn spring-boot:run
```

API base: http://localhost:8080/api/v1; health: http://localhost:8080/actuator/health.

## CI
Pull requests targeting main run backend verification on Java 21 with PostgreSQL 17, including Flyway migration and Hibernate schema validation. See docs/ci.md.
