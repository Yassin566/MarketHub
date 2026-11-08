# MarketHub

Multi-vendor marketplace foundation using a modular monolith.

## Stack
- Java 21, Spring Boot 4.x, Maven
- PostgreSQL, Redis, Flyway
- Angular 21 frontend workspace bootstrap
- Docker Compose, Nginx, GitHub Actions

## Current implementation
The backend foundation includes customer registration/login, BCrypt password hashing, JWT bearer authentication, vendor application submission, admin approval/rejection with role activation only on approval, approved-vendor store profile management, tenant-scoped product CRUD/archive, vendor inventory visibility and stock adjustments, customer inventory reservations with automatic expiry, and persistent customer carts with checkout validation. See the API guides in `docs/`.

Checkout validation rechecks current product/vendor status, prices, currencies, and available stock. It does not create orders, reserve checkout stock, or process payments; those remain separate future phases.

This is an incremental implementation, not a claim that every marketplace capability is complete. Refresh-token rotation, order/checkout processing, payment integration, refunds, payouts, and full Angular business screens remain future work.

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
