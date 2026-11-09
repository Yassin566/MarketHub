import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});

const order = (id: string, productName: string) => ({
  id, status: 'PAID', currency: 'EUR', subtotal: 24, total: 24,
  expiresAt: null, createdAt: '2026-11-08T10:00:00Z',
  items: [{
    productId: id + '-product', vendorId: 'vendor-1',
    productName, quantity: 1, unitPrice: 24, currency: 'EUR', lineTotal: 24,
  }],
});

async function authenticateCustomer(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'test-customer-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'customer-1', email: 'customer@example.test', role: 'CUSTOMER',
    }));
  });
}

test.describe('customer account and order recovery', () => {
  test('paginates order history using server offsets and returns to the previous page', async ({ page }) => {
    await authenticateCustomer(page);
    const requestedOffsets: number[] = [];

    await page.route('**/api/v1/customer/orders**', async route => {
      const url = new URL(route.request().url());
      const offset = Number(url.searchParams.get('offset'));
      requestedOffsets.push(offset);
      const firstPage = offset === 0;
      await route.fulfill({
        json: envelope({
          orders: [firstPage ? order('order-page-one', 'First page item') : order('order-page-two', 'Second page item')],
          limit: 20, offset, hasMore: firstPage,
        }),
      });
    });

    await page.goto('/orders');
    await expect(page.getByText('First page item')).toBeVisible();
    await expect(page.getByText('Showing 1–1')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await page.getByRole('button', { name: 'Next' }).click();

    await expect(page.getByText('Second page item')).toBeVisible();
    await expect(page.getByText('Showing 21–21')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Previous' })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Next' })).toBeDisabled();

    await page.getByRole('button', { name: 'Previous' }).click();
    await expect(page.getByText('First page item')).toBeVisible();
    expect(requestedOffsets).toEqual([0, 20, 0]);
  });

  test('keeps recent orders visible when the notification request independently fails', async ({ page }) => {
    await authenticateCustomer(page);
    await page.route('**/api/v1/customer/orders**', route =>
      route.fulfill({ json: envelope({ orders: [order('order-account', 'Account order item')], limit: 5, offset: 0, hasMore: false }) }),
    );
    await page.route('**/api/v1/customer/notifications**', route =>
      route.fulfill({
        status: 503,
        json: { success: false, data: null, error: { message: 'Unavailable' } },
      }),
    );

    await page.goto('/account');
    await expect(page.getByText('Account order item')).toBeVisible();
    await expect(page.getByRole('alert')).toContainText('Recent notifications could not be loaded');
    await expect(page.getByRole('link', { name: /Order history/ })).toBeVisible();
  });

  test('shows an order access error without requesting fulfilment data', async ({ page }) => {
    await authenticateCustomer(page);
    let fulfilmentReads = 0;
    await page.route('**/api/v1/customer/orders/order-not-owned', route =>
      route.fulfill({
        status: 404,
        json: { success: false, data: null, error: { message: 'Not found' } },
      }),
    );
    await page.route('**/api/v1/customer/orders/order-not-owned/fulfilments', route => {
      fulfilmentReads += 1;
      return route.fulfill({ json: envelope([]) });
    });

    await page.goto('/orders/order-not-owned');
    await expect(page.getByRole('alert')).toContainText('Order not found or you do not have access to it');
    await expect(page.getByRole('heading', { name: 'Fulfilment & tracking' })).toHaveCount(0);
    expect(fulfilmentReads).toBe(0);
  });
});
