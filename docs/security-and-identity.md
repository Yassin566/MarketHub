# Identity and access

Implemented in the foundation: customer-only registration, BCrypt password hashing, email/password login, signed HS256 access JWTs, stateless bearer authentication, method security, and vendor application ownership derived from the authenticated principal. Customer reservation operations require the CUSTOMER role and derive customer ownership from the authenticated principal; a customer cannot release another customer's reservation.

Configuration: JWT_SECRET must contain at least 32 UTF-8 bytes for HS256; use a generated secret in production. JWT_ACCESS_TTL_SECONDS defaults to 900.

Still required before production: opaque refresh-token rotation/reuse detection/revocation, email verification, password reset, MFA, login throttling, vendor approval/admin workflow, CORS allowlist, JWT key rotation, audit events, and managed secrets. Do not expose stack traces in API responses.
