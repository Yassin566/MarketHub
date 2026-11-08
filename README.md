# MarketHub

Multi-vendor marketplace foundation using a modular monolith.

## Stack
- Java 21, Spring Boot 4.x, Maven
- PostgreSQL, Redis, Flyway
- Angular 21 frontend workspace bootstrap
- Docker Compose, Nginx, GitHub Actions

## Current implementation
The initial backend foundation includes customer registration/login, BCrypt password hashing, JWT bearer authentication, vendor application submission with ownership derived from the authenticated principal, a vendor-scoped public product listing, database migrations, and local infrastructure.

This is an incremental implementation, not a claim that every marketplace capability is complete. Refresh-token rotation, vendor approval, full vendor product CRUD, inventory, carts, checkout, payment integration, refunds, payouts, and full Angular business screens remain future work.

## Run locally
```bash
cp .env.example .env
docker compose up -d
cd backend
mvn spring-boot:run
```

API base: `http://localhost:8080/api/v1`; health: `http://localhost:8080/actuator/health`.

See `docs/` for architecture, domain model, API conventions, security, and test strategy. Do not deploy without production secrets, TLS, provider configuration, monitoring, backups, threat modeling, and operational testing.
