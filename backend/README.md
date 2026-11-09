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

## CI environment

The GitHub Actions workflow supplies a disposable test database and test-only JWT settings. Never use these CI credentials outside automated tests. Local development should use a dedicated local database and environment-specific secrets.

The backend CI workflow is triggered for pull requests targeting `main`, pushes to `main`, and manual workflow dispatch.
