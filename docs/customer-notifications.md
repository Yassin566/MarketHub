# Customer notifications

Customer notifications are stored in PostgreSQL and scoped to the authenticated customer.

## API

All endpoints require a customer access token.

- `GET /api/v1/customer/notifications?limit=20&offset=0&unreadOnly=false`
  - Returns a page of notifications, `hasMore`, and the customer's total `unreadCount`.
  - Set `unreadOnly=true` to page only unread items.
  - Page size must be between 1 and 100; offset must be non-negative.
- `PATCH /api/v1/customer/notifications/{notificationId}/read`
  - Marks one notification as read. Repeating the request is safe.
  - Returns 404 for notifications owned by another customer.
- `PATCH /api/v1/customer/notifications/read-all`
  - Marks all unread notifications for the current customer as read and returns the number changed.

## Notification creation

The backend service exposes an idempotent `create` operation for domain events. A unique
(customer, event key) constraint prevents duplicate inbox entries when events are retried.

Current event integrations create notifications for:
- Successful payment confirmation.
- Failed payment outcomes.
- Vendor order moving from paid to processing.
- Vendor order shipment dispatch.

Notification creation runs in the same database transaction as the payment or vendor-order
state transition, so a rolled-back transition cannot leave a misleading notification.

Each notification can reference its customer order. The frontend can use that identifier
to link directly to the order detail page.
