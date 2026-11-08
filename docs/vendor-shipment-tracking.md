# Vendor shipment dispatch and customer tracking

## Vendor dispatch

Authenticated approved vendors dispatch only their own child orders:

    POST /api/v1/vendor/orders/{vendorOrderId}/shipment

Example body:

```json
{
  "carrier": "Example Express",
  "trackingNumber": "ZX123456789",
  "trackingUrl": "https://carrier.example/track/ZX123456789",
  "estimatedDeliveryDate": "2026-11-12"
}
```

Carrier and tracking number are required. Tracking URL and estimated delivery date are optional. The server records the dispatch timestamp and authenticated actor. Tracking URLs, when supplied, must be absolute HTTP or HTTPS URLs with a host.

Dispatch is allowed exactly once and only while the vendor order is PROCESSING. In one transaction, the system creates the immutable shipment record, advances the vendor order to FULFILLED, and writes the existing fulfilment audit record. The generic status endpoint can no longer set FULFILLED directly.

## Customer tracking

Customers can read fulfilment details for their own parent order:

    GET /api/v1/customer/orders/{orderId}/fulfilments

The response contains one entry per vendor child order, vendor display name, fulfilment status, and shipment tracking fields when dispatched. A different customer's order returns 404.

Shipment tracking is informational. This phase does not contact carriers, validate external tracking events, change payment or inventory state, or calculate delivery guarantees.
