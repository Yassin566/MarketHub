import { expect, test } from '@playwright/test';

const envelope = (data: unknown) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});

const product = (version = 0, name = 'Desk Lamp') => ({
  id: 'product-42', name, slug: 'desk-lamp', description: 'Adjustable desk lighting',
  price: 45, currency: 'TND', status: 'ACTIVE', version,
  createdAt: '2026-11-08T10:00:00Z', updatedAt: '2026-11-09T10:00:00Z',
});

const inventory = (version = 3, onHand = 12) => ({
  productId: 'product-42', productName: 'Desk Lamp', onHandQuantity: onHand,
  reservedQuantity: 2, availableQuantity: onHand - 2, version,
  updatedAt: '2026-11-09T10:00:00Z',
});

test.describe('vendor catalogue and inventory operations', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('markethub.access-token', 'vendor-catalogue-token');
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'vendor-42', email: 'vendor@example.test', role: 'VENDOR',
      }));
    });
    await page.route('**/api/v1/vendor/analytics**', route => route.fulfill({
      json: envelope({ from: '2026-10-11', to: '2026-11-09', currencies: [], daily: [], topProducts: [] }),
    }));
    await page.route('**/api/v1/vendor/orders**', route => route.fulfill({
      json: envelope({ orders: [], limit: 20, offset: 0, hasMore: false }),
    }));
  });

  test('creates, edits with the current version, and archives a product', async ({ page }) => {
    let current = product();
    let createBody: Record<string, unknown> | undefined;
    let updateBody: Record<string, unknown> | undefined;
    let archived = false;

    await page.route('**/api/v1/vendor/products', async route => {
      const request = route.request();
      if (request.method() === 'POST') {
        createBody = request.postDataJSON() as Record<string, unknown>;
        current = product(0, String(createBody.name));
        await route.fulfill({ json: envelope(current) });
        return;
      }
      await route.fulfill({ json: envelope(archived ? [] : [current]) });
    });
    await page.route('**/api/v1/vendor/products/**', async route => {
      const request = route.request();
      if (request.method() === 'PUT') {
        updateBody = request.postDataJSON() as Record<string, unknown>;
        current = { ...current, name: String(updateBody.name), price: Number(updateBody.price), version: 1 };
        await route.fulfill({ json: envelope(current) });
        return;
      }
      if (request.method() === 'DELETE') {
        archived = true;
        await route.fulfill({ json: envelope(null) });
        return;
      }
      await route.fallback();
    });
    await page.route('**/api/v1/vendor/inventory', route => route.fulfill({
      json: envelope(archived ? [] : [inventory()]),
    }));

    await page.goto('/vendor');
    await page.getByRole('button', { name: /^Products/ }).click();
    await page.getByLabel('Product name').fill('Desk Lamp');
    await page.getByLabel('URL slug').fill('desk-lamp');
    await page.getByLabel('Price').fill('45');
    await page.getByLabel('Description').fill('Adjustable desk lighting');
    await page.getByRole('button', { name: 'Create product' }).click();

    await expect(page.getByRole('status')).toContainText('Product created.');
    expect(createBody).toEqual({
      name: 'Desk Lamp', slug: 'desk-lamp', description: 'Adjustable desk lighting',
      price: 45, currency: 'TND',
    });
    const row = page.getByRole('row').filter({ hasText: 'Desk Lamp' });
    await expect(row).toContainText('ACTIVE');
    await row.getByRole('button', { name: 'Edit' }).click();
    await expect(page.getByRole('heading', { name: 'Edit product' })).toBeVisible();
    await page.getByLabel('Price').fill('48');
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByRole('status')).toContainText('Product updated.');
    expect(updateBody?.expectedVersion).toBe(0);
    expect(updateBody?.price).toBe(48);

    const updatedRow = page.getByRole('row').filter({ hasText: 'Desk Lamp' });
    page.once('dialog', dialog => dialog.accept());
    await updatedRow.getByRole('button', { name: 'Archive' }).click();
    await expect(page.getByRole('status')).toContainText('Product archived.');
    await expect(page.getByText('No active products yet.')).toBeVisible();
    expect(archived).toBe(true);
  });

  test('records an inventory adjustment and displays its audit history', async ({ page }) => {
    let currentVersion = 3;
    let adjustmentBody: Record<string, unknown> | undefined;
    await page.route('**/api/v1/vendor/products', route => route.fulfill({
      json: envelope([product()]),
    }));
    await page.route('**/api/v1/vendor/inventory', route => route.fulfill({
      json: envelope([inventory(currentVersion, currentVersion === 4 ? 17 : 12)]),
    }));
    await page.route('**/api/v1/vendor/inventory/product-42/adjustments', async route => {
      if (route.request().method() === 'POST') {
        adjustmentBody = route.request().postDataJSON() as Record<string, unknown>;
        currentVersion = 4;
        await route.fulfill({ json: envelope({
          id: 'adjustment-1', productId: 'product-42', quantityDelta: 5,
          quantityBefore: 12, quantityAfter: 17, reason: 'RESTOCK',
          versionAfter: 4, createdAt: '2026-11-09T11:00:00Z',
        }) });
        return;
      }
      await route.fulfill({ json: envelope([{
        id: 'adjustment-1', productId: 'product-42', quantityDelta: 5,
        quantityBefore: 12, quantityAfter: 17, reason: 'RESTOCK',
        versionAfter: 4, createdAt: '2026-11-09T11:00:00Z',
      }]) });
    });

    await page.goto('/vendor');
    await page.getByRole('button', { name: /^Inventory/ }).click();
    const row = page.getByRole('row').filter({ hasText: 'Desk Lamp' });
    await expect(row).toContainText('12');
    await row.getByRole('button', { name: 'Adjust stock' }).click();
    await page.getByLabel('Quantity change').fill('5');
    await page.getByLabel('Reason').selectOption('RESTOCK');
    await page.getByRole('button', { name: 'Save adjustment' }).click();

    await expect(page.getByRole('status')).toContainText('Inventory adjustment saved.');
    expect(adjustmentBody).toEqual({ quantityDelta: 5, reason: 'RESTOCK', expectedVersion: 3 });
    await expect(page.getByRole('row').filter({ hasText: 'Desk Lamp' })).toContainText('17');
    await page.getByRole('row').filter({ hasText: 'Desk Lamp' }).getByRole('button', { name: 'View history' }).click();
    await expect(page.getByRole('heading', { name: 'Adjustment history · Desk Lamp' })).toBeVisible();
    await expect(page.getByRole('row').filter({ hasText: 'RESTOCK' })).toContainText('+5');
    await expect(page.getByRole('row').filter({ hasText: 'RESTOCK' })).toContainText('17');
  });

  test('keeps stock adjustment context when the server reports a version conflict', async ({ page }) => {
    await page.route('**/api/v1/vendor/products', route => route.fulfill({
      json: envelope([product()]),
    }));
    await page.route('**/api/v1/vendor/inventory', route => route.fulfill({
      json: envelope([inventory()]),
    }));
    let submittedVersion: number | undefined;
    await page.route('**/api/v1/vendor/inventory/product-42/adjustments', async route => {
      submittedVersion = Number((route.request().postDataJSON() as Record<string, unknown>).expectedVersion);
      await route.fulfill({
        status: 409,
        json: { success: false, data: null, error: { message: 'Inventory version conflict' } },
      });
    });

    await page.goto('/vendor');
    await page.getByRole('button', { name: /^Inventory/ }).click();
    await page.getByRole('row').filter({ hasText: 'Desk Lamp' }).getByRole('button', { name: 'Adjust stock' }).click();
    await page.getByLabel('Quantity change').fill('-2');
    await page.getByLabel('Reason').selectOption('CORRECTION');
    await page.getByRole('button', { name: 'Save adjustment' }).click();

    await expect(page.getByRole('alert')).toContainText('Inventory changed or the adjustment would use reserved stock');
    await expect(page.getByRole('heading', { name: 'Desk Lamp' }).last()).toBeVisible();
    await expect(page.getByLabel('Quantity change')).toHaveValue('-2');
    expect(submittedVersion).toBe(3);
  });
});
