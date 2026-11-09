import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});

const cart = (quantity: number) => ({
  id: 'cart-lifecycle-1', status: 'ACTIVE', version: quantity,
  createdAt: '2026-11-09T09:00:00Z', updatedAt: '2026-11-09T09:00:00Z',
  items: [{
    productId: 'product-lifecycle-1', productName: 'Ceramic Desk Lamp',
    quantity, unitPrice: 24, currency: 'EUR', availableQuantity: 8,
    productStatus: 'ACTIVE', vendorStatus: 'APPROVED', updatedAt: '2026-11-09T09:00:00Z',
  }],
});

const validation = {
  cartId: 'cart-lifecycle-1', valid: true,
  lines: [{
    productId: 'product-lifecycle-1', productName: 'Ceramic Desk Lamp',
    quantity: 2, unitPrice: 24, currency: 'EUR', lineTotal: 48,
    eligible: true, issue: null,
  }],
  subtotal: 48, currency: 'EUR', issues: [],
};

const pendingOrder = {
  id: 'order-lifecycle-1', status: 'PENDING_PAYMENT', currency: 'EUR',
  subtotal: 48, total: 48, expiresAt: '2026-11-09T10:15:00Z',
  createdAt: '2026-11-09T10:00:00Z',
  items: [{
    productId: 'product-lifecycle-1', vendorId: 'vendor-lifecycle-1',
    productName: 'Ceramic Desk Lamp', quantity: 2, unitPrice: 24,
    currency: 'EUR', lineTotal: 48,
  }],
};

const paidOrder = { ...pendingOrder, status: 'PAID', expiresAt: null };

test.describe('customer purchase lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('markethub.access-token', 'customer-lifecycle-token');
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'customer-lifecycle-1', email: 'customer@example.test', role: 'CUSTOMER',
      }));
    });
  });

  test('updates cart, checks out, requests sandbox payment and follows server-confirmed order through shipment tracking', async ({ page }) => {
    let quantity = 1;
    let cartUpdateBody: Record<string, unknown> | undefined;
    let checkoutKey = '';
    let paymentKey = '';
    let paymentAttempts = 0;

    await page.route('**/api/v1/customer/cart', route =>
      route.fulfill({ json: envelope(cart(quantity)) }));

    await page.route('**/api/v1/customer/cart/items', async route => {
      cartUpdateBody = route.request().postDataJSON() as Record<string, unknown>;
      quantity = Number(cartUpdateBody.quantity);
      await route.fulfill({ json: envelope(cart(quantity)) });
    });

    await page.route('**/api/v1/customer/cart/checkout/validate', async route => {
      await route.fulfill({ json: envelope({
        ...validation,
        lines: validation.lines.map(line => ({ ...line, quantity, lineTotal: line.unitPrice * quantity })),
        subtotal: 24 * quantity,
      }) });
    });

    await page.route('**/api/v1/customer/orders/checkout', async route => {
      checkoutKey = route.request().headers()['idempotency-key'];
      expect(route.request().method()).toBe('POST');
      await route.fulfill({ json: envelope(pendingOrder) });
    });

    await page.route('**/api/v1/customer/orders/order-lifecycle-1/payments', async route => {
      paymentAttempts += 1;
      paymentKey = route.request().headers()['idempotency-key'];
      await route.fulfill({ json: envelope({
        id: 'payment-lifecycle-1', orderId: pendingOrder.id, status: 'REQUESTED',
        providerCode: 'SANDBOX', amount: 48, currency: 'EUR',
        createdAt: '2026-11-09T10:01:00Z',
      }) });
    });

    await page.route('**/api/v1/customer/orders/order-lifecycle-1', route =>
      route.fulfill({ json: envelope(paidOrder) }));

    await page.route('**/api/v1/customer/orders/order-lifecycle-1/fulfilments', route =>
      route.fulfill({ json: envelope([{
        vendorOrderId: 'vendor-order-lifecycle-1', vendorName: 'Northstar Goods',
        status: 'FULFILLED',
        shipment: {
          id: 'shipment-lifecycle-1', carrier: 'Example Express',
          trackingNumber: 'ZX-LIFECYCLE-48',
          trackingUrl: 'https://carrier.example/track/ZX-LIFECYCLE-48',
          shippedAt: '2026-11-09T11:00:00Z',
          estimatedDeliveryDate: '2026-11-12',
        },
      }]) }));

    await page.goto('/cart');
    await expect(page.getByRole('heading', { name: 'Your cart.' })).toBeVisible();
    await expect(page.getByText('Ceramic Desk Lamp')).toBeVisible();
    await page.getByLabel('Quantity').fill('2');
    await page.getByLabel('Quantity').dispatchEvent('change');
    await expect(page.getByRole('status')).toContainText('Cart quantity updated');
    expect(cartUpdateBody).toEqual({ productId: 'product-lifecycle-1', quantity: 2 });

    await page.getByRole('button', { name: /Review checkout/ }).click();
    await expect(page).toHaveURL(/\/checkout$/);
    await expect(page.getByRole('heading', { name: 'Review your order.' })).toBeVisible();
    await expect(page.getByText('Ceramic Desk Lamp × 2')).toBeVisible();
    await expect(page.getByText('€48.00')).toBeVisible();

    await page.getByRole('button', { name: /Place order and request sandbox payment/ }).click();
    await expect(page).toHaveURL(/\/orders\/order-lifecycle-1\?payment=REQUESTED$/);
    expect(checkoutKey).toBeTruthy();
    expect(paymentKey).toBeTruthy();
    expect(paymentAttempts).toBe(1);

    // The order detail endpoint reports PAID only after the server has accepted
    // a verified provider event; the browser redirect/payment request is not proof.
    await expect(page.getByRole('heading', { name: 'Order details.' })).toBeVisible();
    await expect(page.locator('.status-pill').first()).toHaveText('PAID');
    await expect(page.getByText('Ceramic Desk Lamp × 2')).toBeVisible();
    await expect(page.getByText('Order status: PAID. Payment outcomes are confirmed by the server after verified provider events, not by a browser redirect.')).toBeVisible();

    await expect(page.getByRole('heading', { name: 'Fulfilment & tracking' })).toBeVisible();
    await expect(page.getByText('Northstar Goods')).toBeVisible();
    await expect(page.getByText('ZX-LIFECYCLE-48')).toBeVisible();
    await expect(page.getByText(/Estimated delivery/)).toBeVisible();
    const tracking = page.getByRole('link', { name: 'Track with Example Express ↗' });
    await expect(tracking).toHaveAttribute('href', 'https://carrier.example/track/ZX-LIFECYCLE-48');
    await expect(tracking).toHaveAttribute('target', '_blank');
    await expect(tracking).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
