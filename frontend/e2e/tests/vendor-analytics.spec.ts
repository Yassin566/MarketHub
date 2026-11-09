import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});

const report = (from: string, to: string) => ({
  from, to,
  currencies: [{
    currency: 'TND', orderCount: 4, paidOrderCount: 3,
    processingOrderCount: 1, fulfilledOrderCount: 2,
    cancelledOrderCount: 1, recognizedRevenue: 420,
  }],
  daily: [{ date: from, currency: 'TND', orderCount: 2, recognizedRevenue: 180 }],
  topProducts: [{
    productId: 'product-1', productName: 'Desk Lamp',
    currency: 'TND', unitsSold: 6, recognizedRevenue: 300,
  }],
});

async function authenticateVendor(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'vendor-analytics-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'vendor-analytics-1', email: 'vendor@example.test', role: 'VENDOR',
    }));
  });
}

async function stubWorkspace(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/vendor/products', route =>
    route.fulfill({ json: envelope([]) }));
  await page.route('**/api/v1/vendor/inventory', route =>
    route.fulfill({ json: envelope([]) }));
  await page.route('**/api/v1/vendor/orders**', route =>
    route.fulfill({ json: envelope({ orders: [], limit: 20, offset: 0, hasMore: false }) }));
}

test.describe('vendor sales analytics', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateVendor(page);
    await stubWorkspace(page);
  });

  test('applies the selected UTC date range and renders currency-separated performance', async ({ page }) => {
    const ranges: string[] = [];
    await page.route('**/api/v1/vendor/analytics**', async route => {
      const url = new URL(route.request().url());
      const from = url.searchParams.get('from') ?? '';
      const to = url.searchParams.get('to') ?? '';
      ranges.push(from + ':' + to);
      await route.fulfill({ json: envelope(report(from, to)) });
    });

    await page.goto('/vendor');
    await page.getByRole('button', { name: 'Analytics' }).click();
    await page.getByLabel('From').fill('2026-10-01');
    await page.getByLabel('To').fill('2026-10-31');
    await page.getByRole('button', { name: 'Apply range' }).click();

    await expect(page.getByText('Reporting period: 2026-10-01 to 2026-10-31')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'TND' })).toBeVisible();
    await expect(page.getByText('Desk Lamp')).toBeVisible();
    await expect(page.getByText('6', { exact: true })).toBeVisible();
    await expect.poll(() => ranges).toContain('2026-10-01:2026-10-31');
  });

  test('rejects reversed and overlong date ranges before making another analytics request', async ({ page }) => {
    let requests = 0;
    await page.route('**/api/v1/vendor/analytics**', async route => {
      requests += 1;
      const url = new URL(route.request().url());
      await route.fulfill({ json: envelope(report(
        url.searchParams.get('from') ?? '',
        url.searchParams.get('to') ?? '',
      )) });
    });

    await page.goto('/vendor');
    await page.getByRole('button', { name: 'Analytics' }).click();
    await expect.poll(() => requests).toBe(1);

    await page.getByLabel('From').fill('2026-11-10');
    await page.getByLabel('To').fill('2026-11-09');
    await page.getByRole('button', { name: 'Apply range' }).click();
    await expect(page.getByRole('alert')).toContainText('Choose a valid date range.');
    expect(requests).toBe(1);

    await page.getByLabel('From').fill('2025-10-01');
    await page.getByLabel('To').fill('2026-11-09');
    await page.getByRole('button', { name: 'Apply range' }).click();
    await expect(page.getByRole('alert')).toContainText('Choose a date range of 366 days or fewer.');
    expect(requests).toBe(1);
  });

  test('recovers from an analytics outage and displays the retry result', async ({ page }) => {
    let requests = 0;
    await page.route('**/api/v1/vendor/analytics**', async route => {
      requests += 1;
      if (requests === 1) {
        await route.fulfill({
          status: 503,
          json: { success: false, data: null, error: { message: 'Temporarily unavailable' } },
        });
        return;
      }
      const url = new URL(route.request().url());
      await route.fulfill({ json: envelope(report(
        url.searchParams.get('from') ?? '',
        url.searchParams.get('to') ?? '',
      )) });
    });

    await page.goto('/vendor');
    await expect.poll(() => requests).toBe(1);
    await page.getByRole('button', { name: 'Analytics' }).click();
    await expect(page.getByText('Desk Lamp')).toBeVisible();
    await expect(page.getByText(/Reporting period:/)).toBeVisible();
    expect(requests).toBe(2);
  });
});
