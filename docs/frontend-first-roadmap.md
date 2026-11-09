# MarketHub Frontend-First Implementation Roadmap

**Status:** Planning document  
**Repository:** `Yassin566/MarketHub`  
**Baseline:** PR #36 merged; main at `7e716c80babab4ac229db21456db7f57b258d8c2` when this plan was recorded.  
**Execution rule:** Deliver one focused, tested pull request at a time. Frontend work is the priority. Backend changes are allowed only when an actual user-facing requirement cannot be completed with the existing API.

## Product direction

Turn the existing Angular application into a complete, coherent marketplace for three audiences:

1. **Customers** discover products, purchase them, follow orders, receive notifications, and review purchases.
2. **Vendors** manage their storefront catalogue, inventory, orders, fulfilment, and performance.
3. **Administrators** manage vendors, products, reviews, payment exceptions, and marketplace operations.

The current backend already provides substantial capabilities: catalogue and product detail APIs, cart and checkout validation, order creation and retrieval, sandbox payment initiation, fulfilment/shipment information, customer notifications, product reviews, vendor products/inventory/orders/analytics, and admin payment/review/product management. Reuse these APIs rather than rebuilding their functionality.

## Delivery principles

- Inspect the live API controllers, DTOs, authorization rules, and frontend service methods before implementing each feature.
- Do not invent endpoints, response fields, product images, categories, profile fields, or business rules.
- Keep backend changes out of frontend-only phases unless a verified API gap blocks a valuable feature.
- If a backend change is needed, document the gap, add authorization and validation, add migration only when required, and add backend tests before wiring the UI.
- Keep customer, vendor, and admin permissions enforced by the backend; frontend route guards are usability controls, not security boundaries.
- Preserve idempotency keys and server-side price/stock validation in checkout.
- Treat sandbox payments as sandbox payments. Never imply that a real payment was collected.
- Maintain responsive layouts, accessible labels/focus states, clear loading/empty/error/success states, and safe confirmation for destructive actions.
- Every PR should include relevant Angular unit tests and Playwright coverage. Run backend tests whenever backend files change.
- Verify CI on the exact latest PR head SHA before calling a PR successful. Do not merge without an explicit merge instruction.

## Phases

### Phase 1 — Customer storefront and product discovery

**Goal:** Make browsing and finding products feel like a polished marketplace.

Frontend work:
- Improve catalogue layout, responsive product cards, visual hierarchy, and empty/loading/error states.
- Add sorting and filters only for fields supported by the existing catalogue API (for example price or availability if exposed).
- Improve search UX, clear-search behavior, pagination, and query-state preservation.
- Show accurate price, currency, rating, review count, and availability using API data.
- Improve product detail layout and related navigation.
- Add category navigation only if category data and filtering are already exposed by the backend.

Backend gate:
- Inspect catalogue DTOs and query parameters first.
- If categories or a needed filter are absent, decide whether a small API addition is justified; do not fabricate category values in the frontend.

Acceptance:
- Search, filters, sorting, pagination, and product navigation work against real API responses.
- No fake product data is introduced.
- Desktop and mobile Playwright coverage passes.

### Phase 2 — Product detail, reviews, and purchase confidence

**Goal:** Make product pages informative and support the complete review lifecycle.

Frontend work:
- Improve product detail information hierarchy and stock/availability messaging.
- Present rating summaries and paginated published reviews.
- Improve create/edit/delete review UX, validation, confirmation, and success/error feedback.
- Clearly explain that reviews require an eligible fulfilled purchase when the API rejects an ineligible customer.
- Display vendor/store information only where the product API supplies it.

Backend gate:
- Verify whether product detail responses include vendor display name, description, image references, stock/availability, and other desired fields.
- Add only the missing, safe public fields that are genuinely needed; preserve review publication and purchase-eligibility rules.

Acceptance:
- Existing review remains editable after page reload.
- Review actions respect server ownership and eligibility.
- Unpublished reviews never appear in public product pages.

### Phase 3 — Cart, checkout, and sandbox payment journey

**Goal:** Make the purchase flow understandable, resilient, and trustworthy using the current checkout and payment APIs.

Frontend work:
- Improve cart item layout, quantity updates, removal, and stock warnings.
- Explain checkout validation issues with links/actions to correct the cart.
- Present server-calculated line totals and subtotal.
- Prevent duplicate checkout/payment submissions and preserve idempotency behavior.
- Add clear order-created and sandbox-payment-pending/success/failure states based on authoritative API responses.
- Provide a recovery path for timeout, expired reservation, and retry scenarios supported by the API.

Backend gate:
- Inspect existing payment status/read endpoints and webhook-driven status transitions.
- If customers cannot retrieve their own payment status after initiation, add a narrowly scoped customer-owned read endpoint only if needed for a reliable status page.
- Do not add real gateway integration as part of this frontend phase.

Acceptance:
- Customer can browse, add/update/remove cart items, validate checkout, create an order, and follow the sandbox payment state.
- No client-side price is treated as authoritative.
- Duplicate clicks do not create duplicate orders or payments.

### Phase 4 — Customer account, order history, and fulfilment tracking

**Goal:** Give customers a useful account area after purchase.

Frontend work:
- Build a customer account dashboard from existing customer-owned data.
- Improve order history with status filters, pagination, totals, and dates.
- Improve order detail with line items, order state, fulfilment per vendor, shipment carrier, tracking number/link, and estimated delivery when returned.
- Add clear empty states and links between orders, notifications, and products.
- Improve notification inbox filters, unread count, pagination, mark-one-read, and mark-all-read.

Backend gate:
- Inspect existing customer profile and address endpoints before planning profile/address screens.
- If order/payment status is insufficient for the desired customer view, add customer-scoped read APIs with ownership tests.
- Do not show vendor-private or admin-only fields to customers.

Acceptance:
- Customer sees only their own account/order/notification data.
- Order and shipment timelines reflect API state, not guessed progress.

### Phase 5 — Vendor workspace completion

**Goal:** Make the vendor portal a practical day-to-day store management workspace.

Frontend work:
- Improve vendor dashboard metrics and analytics date-range controls/charts.
- Improve product list, create/edit forms, validation, archive confirmation, and optimistic/error recovery.
- Improve inventory table, low-stock highlighting, adjustment history, reason capture, and version-conflict handling.
- Improve vendor order queue with status filters and pagination.
- Improve processing and shipment forms, tracking link validation, and dispatch confirmation.
- Show order status history and shipment details.
- Add store profile screens only if existing APIs support them.

Backend gate:
- Existing frontend service exposes vendor products, inventory, adjustments, orders, analytics, status updates, and shipment creation; verify controller contracts and edge cases.
- Verify whether inventory adjustment history and store profile endpoints exist. Add them only if required and absent.
- Do not expose customer private details beyond what vendors need to fulfil their own orders.

Acceptance:
- Vendor can complete supported product, stock, order, and dispatch workflows without manual API calls.
- Vendor data is isolated to the authenticated vendor.
- Concurrency/version conflicts are understandable and recoverable.

### Phase 6 — Admin workspace completion

**Goal:** Consolidate existing admin capabilities into a clear operational console.

Frontend work:
- Improve admin navigation, dashboard summary, and shared table/filter/pagination patterns.
- Complete vendor application review and vendor status management using existing admin APIs.
- Improve product activation/deactivation and moderation audit display.
- Improve review moderation filters and audit history.
- Improve payment review/resolution and reconciliation history.
- Add detail drawers/pages where API responses provide enough information.
- Add confirmation and reason capture for sensitive admin actions.

Backend gate:
- Vendor admin APIs are reported to exist; inspect their exact routes, permissions, request DTOs, and response models before building the screens.
- If a required vendor action or audit detail is absent, add the smallest role-protected API and tests.
- Never rely on hiding admin buttons as authorization.

Acceptance:
- Admin screens use real data and existing endpoints.
- Every sensitive change records and displays available audit information.
- Non-admin users receive a denied response from the backend.

### Phase 7 — Authentication and account UX

**Goal:** Make existing authentication flows understandable and robust in the browser.

Frontend work:
- Polish login and registration forms, validation, password visibility, and error messages.
- Improve session restoration, expiry handling, logout, and redirect-to-original-destination behavior.
- Ensure role-aware navigation and account landing pages.
- Build password reset/email verification screens only when corresponding backend endpoints already exist.
- Avoid leaking whether an email address is registered through overly specific errors.

Backend gate:
- Verify which authentication endpoints currently exist before designing flows.
- If password reset, email verification, or profile update APIs are missing, record them as explicit backend prerequisites rather than shipping nonfunctional forms.

Acceptance:
- Authentication state is consistent across refreshes and protected routes.
- Expired/invalid sessions fail safely and recover cleanly.
- Backend remains the source of truth for roles and permissions.

### Phase 8 — Shared design system and responsive polish

**Status:** In progress — shared interaction tokens, keyboard focus, reduced-motion support, mobile navigation, and role-specific customer/vendor/admin layouts are being consolidated. PR #50 covers the shared foundation; the follow-up responsive screen pass is tracked in PR #51.

**Goal:** Make the entire product feel like one cohesive application.

Frontend work:
- Establish reusable typography, spacing, color tokens, buttons, inputs, cards, badges, dialogs, tables, and alerts.
- Standardize loading skeletons, empty states, error states, and success feedback.
- Improve mobile navigation and responsive customer/vendor/admin layouts.
- Add keyboard navigation, visible focus, semantic headings, accessible dialogs, and screen-reader labels.
- Check contrast, touch target size, and form error associations.
- Remove duplicated component styling where practical.

Backend changes:
- None expected.

Acceptance:
- Consistent visual and interaction patterns across all roles.
- Key journeys usable on narrow mobile screens and desktop.
- Accessibility checks included in regression review.

### Phase 9 — End-to-end journeys and frontend quality

**Goal:** Validate the complete application as real users would use it.

Test journeys:
- Customer: register/login, browse/search, product detail, add to cart, checkout validation, order creation, sandbox payment state, order tracking, notifications, and review lifecycle.
- Vendor: login, create/update/archive product, adjust inventory, view orders, process order, dispatch shipment, and inspect analytics.
- Admin: review vendor applications/status where supported, manage products, moderate reviews, and resolve payment exceptions.
- Access control: anonymous, customer, vendor, and admin route/API boundaries.
- Responsive smoke tests for mobile and desktop.

Quality:
- Run Angular production build and unit tests.
- Run Playwright E2E suite.
- Add visual regression only if the current CI infrastructure supports it reliably.
- Fix flaky tests instead of weakening meaningful assertions.

Backend changes:
- Only add or adjust integration tests if a backend change is made or an API defect is found.

Acceptance:
- Critical journeys pass in CI on the exact PR commit.
- No known critical console errors, broken routes, or role leaks.

### Phase 10 — Backend gaps discovered during frontend delivery

This is a conditional workstream, not a reason to pause frontend progress. Create backend PRs only when a concrete UI requirement is blocked by missing API capability.

Potential gaps to verify before implementation:
- Product image upload/storage and public image delivery.
- Product categories and category-aware catalogue filtering.
- Customer profile/address retrieval and editing.
- Customer payment status/history retrieval.
- Vendor store profile management.
- Vendor application review actions and audit history.
- Any missing pagination/filtering needed by large customer/vendor/admin lists.

For every proposed backend addition:
1. Record the user-facing requirement and why current endpoints cannot satisfy it.
2. Inspect the database model and existing API conventions.
3. Define authorization, ownership, validation, and error behavior.
4. Implement backend tests (including unauthorized and cross-owner cases).
5. Add migration only if the data model truly needs one.
6. Wire the Angular UI and add Playwright coverage.
7. Update API documentation.

## Recommended execution sequence

1. Phase 1 — Customer storefront and discovery.
2. Phase 2 — Product details and reviews.
3. Phase 3 — Cart, checkout, and sandbox payment journey.
4. Phase 4 — Customer account, orders, and fulfilment.
5. Phase 5 — Vendor workspace.
6. Phase 6 — Admin workspace.
7. Phase 7 — Authentication UX.
8. Phase 8 — Shared design system and responsive polish (apply improvements incrementally throughout, then consolidate).
9. Phase 9 — End-to-end journeys and quality hardening.
10. Phase 10 — Backend gaps, implemented only when proven necessary and preferably alongside the relevant frontend phase.

## Current next action

Start **Phase 1 — Customer storefront and product discovery**.

Before coding:
- Inspect the current catalogue/product controllers, DTOs, query parameters, and Angular catalogue components/services.
- List which filters/sort fields are truly supported.
- Identify whether product images and categories are available.
- Implement only supported capabilities first; raise a small backend prerequisite only if the inspection proves it is needed.

## Progress tracking

For each phase, update this document with:
- Status: Not started / In progress / Blocked / Complete.
- PR number and link.
- Backend changes (if any).
- Tests and CI results on the final PR head SHA.
- Remaining known limitations.

Do not mark a phase complete until its acceptance criteria are met and the corresponding changes are merged.
