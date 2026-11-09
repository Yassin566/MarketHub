# Admin product catalogue management

All endpoints require an authenticated `ADMIN` or `SUPER_ADMIN` role.

## List products

`GET /api/v1/admin/products?search=&status=&limit=20&offset=0`

Returns products across all vendors, including inactive and draft products. Search matches product name or vendor display name. Supported status filters are `ACTIVE`, `INACTIVE`, and `DRAFT`; pagination is limited to 100 records.

## Change catalogue visibility

- `PATCH /api/v1/admin/products/{productId}/deactivation`
- `PATCH /api/v1/admin/products/{productId}/activation`

Request body: `{"reason":"Verified policy violation reference"}`

The authenticated administrator is taken from the security principal. Every actual status change writes an immutable audit event containing previous and resulting status, actor, reason, and timestamp. Repeating a request for the current status is idempotent and does not create a duplicate event.

Deactivation sets product status to `INACTIVE`, which removes it from public catalogue queries. Products and associated order history are retained. Activation restores `ACTIVE` visibility. Draft products cannot be toggled through these endpoints.
