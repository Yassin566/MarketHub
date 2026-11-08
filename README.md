# MarketHub

A multi-vendor commerce platform built as a modular monolith with Java 21, Spring Boot, PostgreSQL, Redis, Flyway, JWT authentication, and an Angular frontend.

## Project status

Foundation and Phase 2 (identity, JWT authentication, and vendor application submission) are in progress. This repository is the source of truth for the implementation.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Compose

## Local infrastructure

```bash
cp .env.example .env
docker compose up -d
```

## Backend

```bash
cd backend
mvn spring-boot:run
```

See `docs/` for architecture, domain model, API conventions, security, and testing strategy.
