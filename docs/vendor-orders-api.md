# Vendor order management

All endpoints require an authenticated VENDOR account whose vendor is APPROVED. Vendor identity is resolved from the authenticated principal; clients cannot select a vendor ID.

## List vendor orders

GET /api/v1/vendor/orders?limit=20&offset=0

Returns a newest-first page of this vendor's child orders, including immutable item/price snapshots. The page size must be 1–100 and offset must be non-negative.

## Read a vendor order

GET /api/v1/vendor/orders/<vendor-order-uuid>

The order, items, and fulfilment history are scoped to the authenticated vendor. An order belonging to another vendor is reported as not found.

## Update fulfilment status

PATCH /api/v1/vendor/orders/<vendor-order-uuid>/status

Request body:

```json
{
  "status": "PROCESSING",
  "note": "Items are being prepared"
}
```

Only these forward transitions are permitted:

- PAID → PROCESSING
- PROCESSING → FULFILLED

Every transition is performed under a row lock and recorded with the acting user, vendor, previous/new status, note, and timestamp in an append-only history table. Invalid or repeated transitions return a conflict. The note is required and limited to 500 characters.

Payment lifecycle states (PENDING_PAYMENT, CANCELLED, EXPIRED, and payment reconciliation) are not vendor-editable. This API does not alter customer order status, payments, inventory quantities, reservations, refunds, shipping, or payouts.
