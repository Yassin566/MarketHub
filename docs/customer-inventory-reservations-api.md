# Customer inventory reservation API

Base path: `/api/v1/customer/reservations`. All endpoints require a bearer access token with the `CUSTOMER` role. Customer identity is taken from the authenticated principal; no customer ID is accepted from the request.

## Create a reservation

`POST /api/v1/customer/reservations`

Request:

```json
{
  "productId": "UUID",
  "quantity": 2
}
```

Quantity must be between 1 and 1000. A successful reservation holds stock for 15 minutes and returns its ID, product, quantity, status, and expiry timestamp. Only active products belonging to approved vendors can be reserved. Insufficient available stock returns HTTP 409.

## List reservations

`GET /api/v1/customer/reservations`

Returns the authenticated customer's latest 100 reservations. Expired reservations are processed before the response.

## Release a reservation

`DELETE /api/v1/customer/reservations/{reservationId}`

Releases an active reservation and returns the updated reservation. Repeating the request is safe: a reservation that is already released, expired, or consumed is returned without releasing stock again. A reservation belonging to another customer returns the same 404 response as a missing reservation.

## Lifecycle and consistency

- Reservation creation locks the product inventory row and checks `on_hand_quantity - reserved_quantity` before creating the reservation and increasing reserved quantity in the same transaction.
- Release and expiry decrement reserved quantity and update reservation status in the same transaction.
- A scheduled worker processes expired reservations in bounded batches using `FOR UPDATE SKIP LOCKED`, so multiple application instances do not process the same reservation concurrently.
- Expiry runs every 30 seconds by default; override with `MARKETHUB_INVENTORY_RESERVATION_EXPIRY_DELAY_MS` only if the matching Spring property is configured.
- Checkout consumption and payment/order creation are deliberately not part of this phase. A future checkout flow must consume reservations idempotently and snapshot prices before contacting payment providers.
