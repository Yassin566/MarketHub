# Customer account and order tracking

## Customer experience

The customer account route (`/account`) is a customer-role-only dashboard. It brings together:
- Shortcuts to the cart, order history, and notification centre.
- The five most recent customer orders.
- The five most recent customer notifications, including links to their related orders.
- The current unread-notification count shared with the application navigation.

Order history is paginated in batches of 20 and provides a retry action when a page cannot be loaded.

## Existing API contracts reused

| Experience | Customer API |
| --- | --- |
| Recent orders and order history | `GET /api/v1/customer/orders?limit=&offset=` |
| Order details | `GET /api/v1/customer/orders/{orderId}` |
| Fulfilment and shipment tracking | `GET /api/v1/customer/orders/{orderId}/fulfilments` |
| Recent notifications | `GET /api/v1/customer/notifications?limit=&offset=` |

The order detail page already renders fulfilment status, carrier, tracking number, tracking URL, and estimated delivery date when supplied by the API.

All endpoints are customer-scoped by the authenticated server principal. The frontend does not send a customer identifier to select account data.

## Payment status

The account dashboard does not infer payment success from navigation or query parameters. Payment/order state is displayed from the server-owned order response and verified provider events.

## Validation

Playwright coverage in `frontend/e2e/tests/customer-account.spec.ts` verifies:
- The account dashboard displays recent orders and order-linked notifications.
- Order history recovers after a transient API failure.
- Pagination controls reflect the API's `hasMore` response.

Run the frontend checks from `frontend`:

```bash
npm test
npm run build
```

Run browser tests from `frontend/e2e`:

```bash
npm install
npx playwright install chromium
npm test
```
