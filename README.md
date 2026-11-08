# MarketHub

Multi-vendor marketplace foundation using a modular monolith.

## Stack
- Java 21, Spring Boot 4.x, Maven
- PostgreSQL, Redis, Flyway
- Angular 21 frontend workspace bootstrap
- Docker Compose, Nginx, GitHub Actions

## Current implementation
The backend foundation includes customer registration/login, BCrypt password hashing, JWT bearer authentication, vendor application submission and admin lifecycle, approved-vendor store profile management, tenant-scoped product CRUD/archive, vendor inventory visibility and stock adjustments, customer inventory reservations with automatic expiry, persistent customer carts, checkout validation, and transactional order creation with per-vendor child orders and expiring stock reservations. See the API guides in `docs/`.

Order creation snapshots product names and prices, creates the parent order and vendor orders, reserves inventory, and clears the cart in one PostgreSQL transaction. Idempotency keys make retries safe. Orders remain pending payment; no payment provider is called and no order is marked paid by a browser action.

This is an incremental implementation, not a claim that every marketplace capability is complete. Payment integration and verified webhooks, taxes, shipping, discounts, commission/payout accounting, refunds, refresh-token rotation, and full Angular business screens remain future work.

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
