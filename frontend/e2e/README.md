# MarketHub browser tests

This isolated Playwright project exercises the Angular frontend in a real Chromium browser. It does not add test dependencies to the production frontend bundle.

## Requirements

- Node.js 22.12+
- npm
- Chromium (installed by Playwright below)
- Optional: MarketHub backend on `http://localhost:8080`. The smoke suite checks that the catalogue handles an unavailable API, so the backend is not required for the current tests.

## Install and run

From the repository root:

```bash
cd frontend/e2e
npm install
npx playwright install chromium
npm test
```

The Playwright web server starts the Angular app at `http://127.0.0.1:4200` using the existing frontend proxy configuration. Tests leave the backend optional; authenticated checkout, order, and vendor workflows will need a running backend and dedicated seeded test accounts before being added.

Useful commands:

```bash
npm run test:headed
npm run test:ui
```

On Linux CI agents, use `npx playwright install --with-deps chromium`.
