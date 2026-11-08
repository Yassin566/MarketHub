# Customer cart API

All endpoints require an authenticated customer access token. Cart identity is derived from the authenticated principal; clients cannot supply a customer or cart ID.

## Read the persistent cart

GET /api/v1/customer/cart

Returns the current customer's cart and current product details. Items remain saved between sessions.

## Add or replace an item

PUT /api/v1/customer/cart/items with JSON body {"productId":"<product-uuid>","quantity":2}.

The product must currently be active and belong to an approved vendor. Repeating the request replaces the quantity. Quantity must be between 1 and 1000.

## Remove an item

DELETE /api/v1/customer/cart/items/<product-uuid>

## Validate checkout

POST /api/v1/customer/cart/checkout/validate

Rechecks current product/vendor status, current price/currency, and available stock (on_hand_quantity - reserved_quantity). The response reports eligible lines and issues. Validation does not reserve stock, create an order, or initiate payment. Inventory can change after validation, so a future order-creation operation must reserve stock transactionally.
