import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({ success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z' });
const order = (id: string, status: string) => ({
  id, customerOrderId: 'customer-order-' + id, status, currency: 'TND', subtotal: 45,
  createdAt: '2026-11-08T10:00:00Z',
  items: [{ id: 'line-' + id, productId: 'product-1', productName: 'Desk Lamp', quantity: 1, unitPrice: 45, currency: 'TND', lineTotal: 45 }],
  statusHistory: [{ id: 'history-' + id, previousStatus: 'PAID', newStatus: status, note: 'Vendor update', createdAt: '2026-11-08T11:00:00Z' }],
  shipment: null,
});

test.describe('vendor order management', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('markethub.access-token', 'test-vendor-token');
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'vendor-1', email: 'vendor@example.test', role: 'VENDOR',
      }));
    });
    await page.route('**/api/v1/vendor/products', route => route.fulfill({ json: envelope([]) }));
    await page.route('**/api/v1/vendor/inventory', route => route.fulfill({ json: envelope([]) }));
    await page.route('**/api/v1/vendor/analytics**', route => route.fulfill({ json: envelope({
      from: '2026-10-11', to: '2026-11-09', currencies: [], daily: [], topProducts: [],
    }) }));
  });

  test('filters orders by status and paginates the filtered results', async ({ page }) => {
    const requests: string[] = [];
    await page.route('**/api/v1/vendor/orders**', async route => {
      const url = new URL(route.request().url());
      const status = url.searchParams.get('status');
      const offset = Number(url.searchParams.get('offset') ?? 0);
      requests.push((status ?? 'ALL') + ':' + offset);
      const rows = status === 'PROCESSING'
        ? [order('processing-1', 'PROCESSING')]
        : status === 'PAID'
          ? [order('paid-1', 'PAID')]
          : [order('paid-1', 'PAID'), order('processing-1', 'PROCESSING')];
      await route.fulfill({ json: envelope({ orders: offset === 0 ? rows : [order('processing-2', 'PROCESSING')], limit: 20, offset, hasMore: offset === 0 }) });
    });

    await page.goto('/vendor');
    await page.getByRole('button', { name: /Orders/ }).click();
    await expect(page.getByText('Desk Lamp')).toBeVisible();
    await page.getByLabel('Order status').selectOption('PROCESSING');
    await expect(page.getByText('PROCESSING', { exact: true })).toBeVisible();
    await expect(page.getByText('PAID', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Status history')).toBeVisible();
    await page.getByRole('button', { name: 'Next' }).click();
    await expect.poll(() => requests).toContain('PROCESSING:20');
    await expect(page.getByText('ORDER processi')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Previous' })).toBeEnabled();
  });

  test('shows a recoverable error and an empty filtered state', async ({ page }) => {
    let fail = true;
    await page.route('**/api/v1/vendor/orders**', async route => {
      if (fail) {
        fail = false;
        await route.fulfill({ status: 503, json: { success: false, data: null, error: { message: 'Unavailable' } } });
      } else {
        await route.fulfill({ json: envelope({ orders: [], limit: 20, offset: 0, hasMore: false }) });
      }
    });
    await page.goto('/vendor');
    await page.getByRole('button', { name: /Orders/ }).click();
    await expect(page.getByRole('alert')).toContainText('Orders could not be loaded');
    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByRole('heading', { name: 'No orders yet.' })).toBeVisible();
  });
});
