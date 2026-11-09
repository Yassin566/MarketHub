# Backend development and CI

## Requirements

- Java 21
- Maven (the Maven wrapper is not currently checked in)
- PostgreSQL 17 for integration verification

## Verify

Run the full backend verification from this directory:

```bash
mvn --batch-mode --no-transfer-progress verify
```

The CI workflow runs the same command against a PostgreSQL 17 service container. It applies Flyway migrations and executes the backend unit and integration test suites.

## Local demo data

The comprehensive demo dataset is loaded automatically when the backend starts with the explicit `dev` Spring profile. Flyway migrations complete before Spring Boot invokes the startup runner.

Start the backend from this directory with:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

On PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

The runner executes `src/main/resources/db/demo-seed.sql`. The script uses deterministic IDs and conflict-safe inserts, so restarting the application does not create duplicate demo records. It does not delete existing data.

All demo accounts use the shared development-only password `MarketHubDemo2026!` (17 characters). Re-running the seed updates the demo accounts’ password hashes so existing local demo accounts receive the new password.

**Safety:** use the `dev` profile only with a dedicated local development database. The seed includes demo accounts with a shared development-only password and fictional order/payment data. The seeder is not registered in the default, test, staging, or production profiles. Never activate `dev` against a shared or production database.

## CI environment

The GitHub Actions workflow supplies a disposable test database and test-only JWT settings. Never use these CI credentials outside automated tests. Local development should use a dedicated local database and environment-specific secrets.

The backend CI workflow is triggered for pull requests targeting `main`, pushes to `main`, and manual workflow dispatch.

## PostgreSQL integration-test troubleshooting

If verification fails during database initialization, confirm that PostgreSQL 17 is reachable and that the test database/user/password match the CI service definition. Flyway migration and application integration-test failures should be fixed at their source; do not skip migrations or disable integration tests to obtain a green build.
