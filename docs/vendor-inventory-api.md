# Vendor inventory API (phase 4)

All endpoints use the bearer access token. Inventory operations require an approved vendor; the vendor UUID is derived from the authenticated principal and is never accepted from the request.

## Inventory

- GET /api/v1/vendor/inventory — list non-archived products and their stock.
- POST /api/v1/vendor/inventory/{productId}/adjustments — add or remove stock.
- GET /api/v1/vendor/inventory/{productId}/adjustments — read the latest 100 adjustment records for an owned product.

Adjustment JSON:

```json
{
  "quantityDelta": 12,
  "reason": "RESTOCK",
  "expectedVersion": 0
}
```

Allowed reasons are RESTOCK, CORRECTION, DAMAGE, and RETURN. Positive deltas add stock; negative deltas remove stock. A zero delta is rejected. The expectedVersion is required and must match the current inventory version.

## Consistency and security

- Flyway V4 creates one inventory row per existing product and creates inventory rows atomically when new products are created.
- Database constraints prevent negative stock, negative reservations, and reservations exceeding on-hand stock.
- Every adjustment uses a single conditional SQL update scoped by product, vendor, and expected version. Competing/stale updates return HTTP 409; the update and append-only ledger insert share one transaction.
- A vendor cannot read or adjust another vendor's inventory, even if it knows the product UUID. Foreign inventory is returned as HTTP 404.
- The ledger records the actor, signed delta, before/after quantities, reason, and resulting version.
- Reserved quantity is represented now so future cart/checkout reservation work can use the same invariant. Customer stock reservation and release workflows are not part of this phase.
