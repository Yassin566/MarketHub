# Test strategy

1. Unit tests: commission math, discount allocation, order splitting, state transitions.
2. MVC/security tests: anonymous access, role matrix, vendor A cannot access vendor B.
3. Repository tests: PostgreSQL constraints, optimistic-lock conflicts, migration compatibility.
4. Integration tests: checkout idempotency, inventory reservation expiry, webhook signature/replay.
5. Contract tests: payment provider adapters and API schema.
6. End-to-end tests: customer multi-vendor purchase, vendor fulfillment, refund and moderation.
7. Non-functional: load tests, dependency scanning, SAST/DAST, backup/restore, failure scenarios.

A green application-context test is not production readiness.
