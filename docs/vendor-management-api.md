# Vendor management API (phase 3)

All endpoints use /api/v1 and the existing bearer access token. Vendor APIs derive the vendor record from the authenticated user's UUID and require vendor status APPROVED; callers cannot choose a vendor ID.

## Vendor application review (ADMIN / SUPER_ADMIN)
- GET /admin/vendor-applications — list pending applications.
- POST /admin/vendor-applications/{vendorId}/approve — approve a pending application and atomically activate the owner's VENDOR role.
- POST /admin/vendor-applications/{vendorId}/reject — reject a pending application. JSON body: {"reason":"..."}.

Only PENDING applications can be approved or rejected. Approval and role activation share a transaction. Rejection does not grant vendor privileges.

## Store profile (approved vendor)
- GET /vendor/store
- POST /vendor/store — create the vendor's single store profile.
- PUT /vendor/store — update it.

Request fields: storeName, slug, optional description, logoUrl, supportEmail, supportPhone.

## Vendor products (approved vendor)
- GET /vendor/products — list this vendor's non-archived products.
- POST /vendor/products — create an ACTIVE product; omit expectedVersion.
- PUT /vendor/products/{productId} — update a product owned by this vendor; include the expectedVersion returned by GET/POST.
- DELETE /vendor/products/{productId} — soft-archive a product.

Product request fields: name, slug, optional description, price (non-negative decimal, up to four fractional digits), and uppercase three-letter currency. Updates require expectedVersion and use a compare-and-set database update; stale versions return HTTP 409 rather than overwriting a newer edit. Product IDs belonging to another vendor are treated as not found.

## Security and persistence notes
- Admin review routes require ADMIN or SUPER_ADMIN through method-level authorization.
- Vendor ownership is always resolved from the authenticated principal, not request parameters.
- Database uniqueness constraints enforce one store per vendor and unique store/product slugs.
- Soft archive preserves historical product rows.

## Automated security verification

`VendorProductHttpSecurityIntegrationTest` exercises the real bearer-token filter, Spring Security chain, MVC controller, and PostgreSQL-backed tenant queries. It verifies that store reads and updates are scoped to the authenticated vendor, that a vendor's product list excludes another vendor's product, and that attempts to update or archive a foreign product return HTTP 404 without changing the foreign row.
