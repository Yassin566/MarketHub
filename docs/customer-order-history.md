# Customer order history

Authenticated customers can list their own orders:

    GET /api/v1/customer/orders?limit=20&offset=0

The response contains a stable newest-first page of order summaries, each including its item snapshots, plus `limit`, `offset`, and `hasMore`. The default page size is 20 and the maximum is 100. Invalid limits or negative offsets are rejected.

Order details remain available at:

    GET /api/v1/customer/orders/{orderId}

Both endpoints require the CUSTOMER role and scope every query to the authenticated customer. An order belonging to another customer is indistinguishable from a missing order (404). Item names, prices, quantities, and currency are the checkout-time snapshots, not live product data.

This phase is read-only; it does not change payment, order, reservation, or inventory state.
