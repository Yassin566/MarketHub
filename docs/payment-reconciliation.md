# Payment reconciliation and stale-payment review

The scheduled review worker is a timeout detector, not an authoritative provider reconciliation integration.

## Behavior

- It scans only payments still in PROCESSING and older than the configured threshold.
- It writes an immutable payment_reconciliation_attempts record and changes the payment status to REQUIRES_REVIEW in one transaction.
- It uses bounded batches and SELECT FOR UPDATE SKIP LOCKED so multiple worker instances do not claim the same payment concurrently.
- It does not call a payment provider, infer success or failure, mark orders paid/cancelled, or modify inventory reservations.
- The attempt explicitly records that provider status was not queried. An operator must verify the provider through an authoritative channel.

## Configuration

The worker is disabled by default.

- MARKETHUB_PAYMENT_RECONCILIATION_ENABLED=false
- MARKETHUB_PAYMENT_RECONCILIATION_STALE_AFTER=PT30M
- MARKETHUB_PAYMENT_RECONCILIATION_POLL_INTERVAL=PT1M
- MARKETHUB_PAYMENT_RECONCILIATION_BATCH_SIZE=50

Batch size is bounded to 1-500. The stale threshold must be at least one second.

## Admin visibility

GET /api/v1/admin/payments/reconciliation?limit=50&offset=0

Requires ADMIN or SUPER_ADMIN. Results are read-only and paginated (maximum 100 per page). They include the payment/order identifiers, provider reference, recorded outcome, observed payment status, detail, and timestamp.

This phase intentionally does not provide a manual state-changing resolution endpoint. Provider lookup, audited operator resolution, refunds, and late-payment compensation require a separate design.
