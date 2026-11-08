# Payment reconciliation and stale-payment review

The scheduled review worker is a timeout detector, not an authoritative provider reconciliation integration.

## Behavior

- It scans only payments still in PROCESSING and older than the configured threshold.
- It writes an immutable payment_reconciliation_attempts record and changes only the payment status to REQUIRES_REVIEW.
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

## Audited manual resolution

After independently verifying the payment with the provider, an ADMIN or SUPER_ADMIN can resolve a payment requiring review:

POST /api/v1/admin/payments/{paymentId}/resolution

Request body:

    {
      "decision": "CONFIRM_SUCCEEDED",
      "evidenceReference": "provider-case-or-transaction-reference",
      "reason": "Provider dashboard confirms captured payment"
    }

Use CONFIRM_FAILED to record an authoritative provider failure. Evidence reference and reason are mandatory. The authenticated administrator identity is recorded by the server; clients cannot supply the actor ID.

A confirmed success consumes inventory and marks the order, vendor orders, and payment paid only when every reservation remains active and unexpired and the order is still awaiting payment. If those conditions no longer hold, the transaction is rejected and the payment remains under review. This prevents late success from silently overselling inventory; refund/compensation is a separate workflow.

A confirmed failure releases any still-active reservations, cancels an order that is still pending payment, and marks the payment failed. Already-expired orders remain expired.

Each payment can have at most one manual resolution. The immutable audit record captures the administrator, decision, prior/resulting payment status, evidence reference, reason, and timestamp. Read it with:

GET /api/v1/admin/payments/{paymentId}/resolution

These endpoints require ADMIN or SUPER_ADMIN. Manual resolution is an operator attestation after external verification; MarketHub does not contact a payment provider.
