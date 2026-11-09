import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});
const order = {
  id: 'order-payment-77', status: 'PENDING_PAYMENT', currency: 'EUR',
  subtotal: 32, total: 32, expiresAt: '2026-11-09T10:15:00Z',
  createdAt: '2026-11-09T10:00:00Z',
  items: [{ productId: 'product-77', vendorId: 'vendor-77', productName: 'Travel Mug',
    quantity: 1, unitPrice: 32, currency: 'EUR', lineTotal: 32 }],
};

async function customerSession(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'customer-payment-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'customer-77', email: 'customer@example.test', role: 'CUSTOMER',
    }));
  });
}

test.describe('customer payment recovery and order notifications', () => {
  test('follows an order notification to the correct order and shipment timeline', async ({ page }) => {
    await customerSession(page);
    await page.route('**/api/v1/customer/notifications**', route => route.fulfill({
      json: envelope({
        notifications: [{
          id: 'notification-payment-77', orderId: order.id, type: 'PAYMENT',
          title: 'Payment is awaiting confirmation',
          message: 'Your order is awaiting a verified sandbox payment update.',
          createdAt: '2026-11-09T10:01:00Z', readAt: null,
        }],
        limit: 20, offset: 0, hasMore: false, unreadCount: 1,
      }),
    }));
    await page.route(`**/api/v1/customer/orders/${order.id}`, route => route.fulfill({ json: envelope(order) }));
    await page.route(`**/api/v1/customer/orders/${order.id}/fulfilments`, route => route.fulfill({
      json: envelope([{
        vendorOrderId: 'vendor-order-77', vendorName: 'Northstar Goods', status: 'PROCESSING', shipment: null,
      }]),
    }));

    await page.goto('/notifications');
    await expect(page.getByText('Payment is awaiting confirmation')).toBeVisible();
    await page.getByRole('link', { name: 'View order' }).click();
    await expect(page).toHaveURL(new RegExp(`/orders/${order.id}$`));
    await expect(page.getByRole('heading', { name: 'Order details.' })).toBeVisible();
    await expect(page.getByText('Travel Mug')).toBeVisible();
    await expect(page.getByText('Payment request')).toBeVisible();
    await expect(page.getByText('Northstar Goods')).toBeVisible();
    await expect(page.getByText('Awaiting dispatch. Tracking details will appear here when this vendor ships your items.')).toBeVisible();
  });

  test('reuses the same payment idempotency key after a failed retry and page reload', async ({ page }) => {
    await customerSession(page);
    const paymentKeys: string[] = [];
    let attempts = 0;
    await page.route(`**/api/v1/customer/orders/${order.id}`, route => route.fulfill({ json: envelope(order) }));
    await page.route(`**/api/v1/customer/orders/${order.id}/fulfilments`, route => route.fulfill({ json: envelope([]) }));
    await page.route(`**/api/v1/customer/orders/${order.id}/payments`, async route => {
      paymentKeys.push(route.request().headers()['idempotency-key']);
      attempts += 1;
      if (attempts <= 2) {
        await route.fulfill({
          status: 503,
          json: { success: false, data: null, error: { message: 'Temporary payment gateway interruption' } },
        });
        return;
      }
      await route.fulfill({ json: envelope({
        id: 'payment-77', orderId: order.id, status: 'REQUESTED',
        providerCode: 'SANDBOX', amount: 32, currency: 'EUR', createdAt: '2026-11-09T10:05:00Z',
      }) });
    });

    await page.goto(`/orders/${order.id}?payment=retry`);
    await page.getByRole('button', { name: 'Retry sandbox payment request' }).click();
    await expect(page.getByRole('alert')).toContainText('payment request could not be confirmed');
    expect(paymentKeys).toHaveLength(1);
    expect(paymentKeys[0]).toBeTruthy();

    await page.reload();
    await expect(page.getByRole('button', { name: 'Retry sandbox payment request' })).toBeVisible();
    await page.getByRole('button', { name: 'Retry sandbox payment request' }).click();
    await expect(page.getByRole('alert')).toContainText('payment request could not be confirmed');
    expect(paymentKeys[1]).toBe(paymentKeys[0]);

    await page.getByRole('button', { name: 'Retry sandbox payment request' }).click();
    await expect(page.getByText('Payment request status:')).toBeVisible();
    await expect(page.getByText('REQUESTED')).toBeVisible();
    expect(paymentKeys).toHaveLength(3);
    expect(paymentKeys.every(key => key === paymentKeys[0])).toBe(true);
  });
});
