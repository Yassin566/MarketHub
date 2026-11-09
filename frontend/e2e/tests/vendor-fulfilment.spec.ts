import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});

const vendorOrder = (status: string, shipment: unknown = null) => ({
  id: 'vendor-order-42',
  customerOrderId: 'customer-order-42',
  status,
  currency: 'TND',
  subtotal: 90,
  createdAt: '2026-11-08T10:00:00Z',
  items: [{
    id: 'line-42', productId: 'product-42', productName: 'Desk Lamp',
    quantity: 2, unitPrice: 45, currency: 'TND', lineTotal: 90,
  }],
  statusHistory: [{
    id: 'history-42', previousStatus: 'PAID', newStatus: status,
    note: 'Vendor accepted order', createdAt: '2026-11-09T09:00:00Z',
  }],
  shipment,
});

async function openVendorOrders(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'vendor-fulfilment-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'vendor-42', email: 'vendor@example.test', role: 'VENDOR',
    }));
  });
  await page.route('**/api/v1/vendor/products', route => route.fulfill({ json: envelope([]) }));
  await page.route('**/api/v1/vendor/inventory', route => route.fulfill({ json: envelope([]) }));
  await page.route('**/api/v1/vendor/analytics**', route => route.fulfill({ json: envelope({
    from: '2026-10-11', to: '2026-11-09', currencies: [], daily: [], topProducts: [],
  }) }));
  await page.goto('/vendor');
  await page.getByRole('button', { name: /Orders/ }).click();
}

test.describe('vendor fulfilment lifecycle', () => {
  test('accepts a paid order, records dispatch and displays shipment details', async ({ page }) => {
    let status = 'PAID';
    let shipment: unknown = null;
    let statusRequest: Record<string, unknown> | undefined;
    let shipmentRequest: Record<string, unknown> | undefined;

    await page.route('**/api/v1/vendor/orders**', async route => {
      const request = route.request();
      if (request.method() === 'PATCH') {
        statusRequest = request.postDataJSON() as Record<string, unknown>;
        status = 'PROCESSING';
        await route.fulfill({ json: envelope(vendorOrder(status)) });
        return;
      }
      if (request.method() === 'POST') {
        shipmentRequest = request.postDataJSON() as Record<string, unknown>;
        status = 'FULFILLED';
        shipment = {
          id: 'shipment-42', carrier: 'Tunis Express', trackingNumber: 'TN-TRACK-42',
          trackingUrl: 'https://carrier.example/track/TN-TRACK-42',
          shippedAt: '2026-11-09T11:00:00Z', estimatedDeliveryDate: '2026-11-12',
        };
        await route.fulfill({ json: envelope(vendorOrder(status, shipment)) });
        return;
      }
      await route.fulfill({ json: envelope({
        orders: [vendorOrder(status, shipment)], limit: 20, offset: 0, hasMore: false,
      }) });
    });

    await openVendorOrders(page);
    await expect(page.getByText('Desk Lamp')).toBeVisible();
    await page.getByRole('button', { name: 'Start processing' }).click();
    await expect(page.getByRole('status')).toContainText('Order moved to processing');
    expect(statusRequest).toEqual({
      status: 'PROCESSING', note: 'Vendor accepted order and started processing.',
    });

    await page.getByRole('button', { name: 'Record dispatch' }).click();
    await page.getByLabel('Carrier').fill('Tunis Express');
    await page.getByLabel('Tracking number').fill('TN-TRACK-42');
    await page.getByLabel('Tracking URL (optional)').fill('https://carrier.example/track/TN-TRACK-42');
    await page.getByLabel('Estimated delivery').fill('2026-11-12');
    await page.getByRole('button', { name: 'Confirm dispatch' }).click();

    await expect(page.getByRole('status')).toContainText('Shipment recorded and order marked fulfilled');
    expect(shipmentRequest).toEqual({
      carrier: 'Tunis Express',
      trackingNumber: 'TN-TRACK-42',
      trackingUrl: 'https://carrier.example/track/TN-TRACK-42',
      estimatedDeliveryDate: '2026-11-12',
    });
    await expect(page.locator('.vendor-order-card .status-pill')).toHaveText('FULFILLED');
    await expect(page.getByText('Shipment · Tunis Express')).toBeVisible();
    const tracking = page.getByRole('link', { name: 'Open tracking ↗' });
    await expect(tracking).toHaveAttribute('href', 'https://carrier.example/track/TN-TRACK-42');
    await expect(tracking).toHaveAttribute('rel', 'noopener noreferrer');
    await expect(page.getByText('Estimated delivery: 2026-11-12')).toBeVisible();
  });

  test('rejects unsafe tracking URL schemes before sending a shipment request', async ({ page }) => {
    let shipmentRequests = 0;
    await page.route('**/api/v1/vendor/orders**', async route => {
      if (route.request().method() === 'POST') shipmentRequests += 1;
      await route.fulfill({ json: envelope({
        orders: [vendorOrder('PROCESSING')], limit: 20, offset: 0, hasMore: false,
      }) });
    });

    await openVendorOrders(page);
    await page.getByRole('button', { name: 'Record dispatch' }).click();
    await page.getByLabel('Carrier').fill('Tunis Express');
    await page.getByLabel('Tracking number').fill('TN-TRACK-42');
    await page.getByLabel('Tracking URL (optional)').fill('javascript:alert(1)');
    await page.getByRole('button', { name: 'Confirm dispatch' }).click();

    await expect(page.locator('.dispatch-panel').getByRole('alert'))
      .toContainText('Enter a valid HTTP or HTTPS tracking URL');
    expect(shipmentRequests).toBe(0);
    await expect(page.getByRole('button', { name: 'Confirm dispatch' })).toBeEnabled();
  });

  test('keeps dispatch details available after a temporary API failure so the vendor can retry', async ({ page }) => {
    let attempts = 0;
    await page.route('**/api/v1/vendor/orders**', async route => {
      if (route.request().method() === 'POST') {
        attempts += 1;
        if (attempts === 1) {
          await route.fulfill({
            status: 503,
            json: { success: false, data: null, error: { message: 'Temporary service interruption' } },
          });
          return;
        }
        await route.fulfill({ json: envelope(vendorOrder('FULFILLED', {
          id: 'shipment-42', carrier: 'Tunis Express', trackingNumber: 'TN-TRACK-42',
          trackingUrl: null, shippedAt: '2026-11-09T11:00:00Z', estimatedDeliveryDate: null,
        })) });
        return;
      }
      const dispatched = attempts > 1;
      await route.fulfill({ json: envelope({
        orders: [vendorOrder(dispatched ? 'FULFILLED' : 'PROCESSING')], limit: 20, offset: 0, hasMore: false,
      }) });
    });

    await openVendorOrders(page);
    await page.getByRole('button', { name: 'Record dispatch' }).click();
    await page.getByLabel('Carrier').fill('Tunis Express');
    await page.getByLabel('Tracking number').fill('TN-TRACK-42');
    await page.getByRole('button', { name: 'Confirm dispatch' }).click();

    await expect(page.getByRole('alert')).toContainText('Shipment could not be recorded');
    await expect(page.getByLabel('Carrier')).toHaveValue('Tunis Express');
    await expect(page.getByLabel('Tracking number')).toHaveValue('TN-TRACK-42');
    await page.getByRole('button', { name: 'Confirm dispatch' }).click();

    await expect(page.getByRole('status')).toContainText('Shipment recorded and order marked fulfilled');
    expect(attempts).toBe(2);
    await expect(page.locator('.vendor-order-card .status-pill')).toHaveText('FULFILLED');
  });
});
