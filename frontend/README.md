# MarketHub frontend

Angular 21 standalone application with strict TypeScript, lazy routes, SCSS, and a development proxy to the Spring Boot API.

## Requirements
- Node.js 22.12+ (or a version supported by Angular CLI 21)
- npm
- Backend running at http://localhost:8080

## Run locally
```bash
cd frontend
npm install
npm start
```
Open http://localhost:4200. The development proxy forwards `/api` and `/actuator` to the backend.

## Current scope
- Responsive public landing page and application shell.
- Customer registration and login using the existing auth API.
- Short-lived access token held in sessionStorage and attached by an HTTP interceptor.
- Route guards and role-aware session model.
- No refresh-token flow exists in the backend yet; signing out clears the browser session.

Registration creates a CUSTOMER account. Vendor accounts remain subject to the backend vendor approval process.

## Frontend roadmap
1. Storefront and product discovery.
2. Customer cart, checkout, payment request, order history, and shipment tracking.
3. Vendor dashboard, catalog, inventory, orders, and dispatch.
4. Admin vendor/payment operations.
5. Accessibility, end-to-end coverage, and production deployment configuration.
