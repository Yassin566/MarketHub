# MarketHub

Multi-vendor marketplace foundation using a modular monolith.

## Stack
- Java 21, Spring Boot 4.x, Maven
- PostgreSQL, Redis, Flyway
- Angular 21 frontend workspace bootstrap
- Docker Compose, Nginx, GitHub Actions

## Current implementation

The backend foundation includes customer registration/login, JWT bearer authentication, vendor application and approval lifecycle, vendor store profiles, tenant-scoped product and inventory management, customer carts and reservations, transactional order creation, and durable idempotent payment-request records with a transactional outbox. See the API guides in `docs/`.

Payment requests persist the order's server-calculated amount and currency. The outbox event is committed atomically with the payment attempt. No payment provider is configured yet: these records are not charges, orders remain pending payment, and fulfillment is not triggered.

This is an incremental implementation, not a claim that every marketplace capability is complete. Provider integration, outbox delivery, signed webhooks, refunds, taxes, shipping, commission/payout accounting, refresh-token rotation, and full Angular business screens remain future work.

## Run locally
```bash
cp .env.example .env
docker compose up -d
cd backend
mvn spring-boot:run
```

API base: `http://localhost:8080/api/v1`; health: `http://localhost:8080/actuator/health`.

## CI
Pull requests targeting `main` run backend verification on Java 21 with PostgreSQL 17, including Flyway startup/migration validation and Hibernate schema validation. See [docs/ci.md](docs/ci.md) for the workflow scope and remaining test coverage.

See `docs/` for architecture, domain model, API conventions, security, and test strategy. Do not deploy without production secrets, TLS, provider configuration, monitoring, backups, threat modeling, and operational testing.
