# Continuous integration

The `backend-ci` workflow runs for every pull request targeting `main`, pushes
to `main`, and manual dispatches. Running on every PR avoids a skipped required
status when a documentation-only PR is opened.

## Backend verification

The job runs on Java 21 and provisions an ephemeral PostgreSQL 17 service.
It executes:

```bash
cd backend
mvn --batch-mode --no-transfer-progress verify
```

The application datasource is pointed at the service database through
`DATABASE_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`. This causes the
Spring application context tests to exercise Flyway startup/migrations and
Hibernate schema validation against PostgreSQL, in addition to the unit tests.

The database credentials and JWT secret in this workflow are disposable
test-only values. Do not reuse them outside CI.

## Current scope and follow-up

- PostgreSQL-backed application startup and migration validation are covered.
- Existing unit tests still include mocked repository/JDBC behavior; this is
  not yet a full HTTP-level, multi-vendor authorization integration suite.
- Add Testcontainers or extend the PostgreSQL service tests for end-to-end
  API scenarios covering approval/rejection, vendor role activation, and
  cross-vendor product/store access.
- Add a frontend job when the Angular workspace and its package scripts are
  present in the repository.
- Configure repository branch protection separately in GitHub settings:
  require the backend verification check before merging into `main`.
