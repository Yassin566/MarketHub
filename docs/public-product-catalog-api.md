# Public product catalogue API

The public storefront exposes active products without requiring a customer session.

## List products

`GET /api/v1/products?page=0&size=12&search=lamp`

- `page`: zero-based page index; defaults to `0`.
- `size`: page size from 1 to 48; defaults to `12`.
- `search`: optional case-insensitive product-name search.
- Results include active products only and are ordered newest first.

The response uses the standard `ApiResponse` envelope. `data` is a Spring page with `content`, `totalElements`, `totalPages`, `number`, and `size`.

## Product detail

`GET /api/v1/products/{slug}`

Returns the active product matching the slug, or `404 NOT_FOUND` when it does not exist or is not active.

## Product fields

Each product exposes `id`, `vendorId`, `name`, `slug`, `price`, `currency`, and `createdAt`.

These endpoints are public read-only catalogue operations. Vendor management and product mutation endpoints remain authenticated and vendor-scoped.
