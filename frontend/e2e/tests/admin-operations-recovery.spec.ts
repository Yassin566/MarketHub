import { expect, test } from '@playwright/test';

const response = <T>(data: T) => ({
  success: true, data, error: null, timestamp: '2026-11-09T12:00:00Z',
});

const vendorId = '77777777-7777-4777-8777-777777777777';
const productId = '55555555-5555-4555-8555-555555555555';

async function signInAsAdmin(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'admin-recovery-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: '33333333-3333-4333-8333-333333333333',
      email: 'admin@example.test',
      role: 'ADMIN',
    }));
  });
}

async function stubUnrelatedAdminApis(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/admin/payments/reconciliation**', route =>
    route.fulfill({ json: response({ items: [], total: 0, limit: 20, offset: 0 }) }));
  await page.route('**/api/v1/admin/payments**', route =>
    route.fulfill({ json: response({ items: [], total: 0, limit: 100, offset: 0 }) }));
  await page.route('**/api/v1/admin/reviews**', route =>
    route.fulfill({ json: response({ items: [], total: 0, limit: 100, offset: 0 }) }));
  await page.route('**/api/v1/admin/vendors**', route =>
    route.fulfill({ json: response({ items: [], total: 0, limit: 20, offset: 0 }) }));
  await page.route('**/api/v1/admin/vendor-applications**', route =>
    route.fulfill({ json: response([]) }));
}

test('product moderation conflict leaves the product unchanged and reports the server error', async ({ page }) => {
  await signInAsAdmin(page);
  await stubUnrelatedAdminApis(page);
  let submittedReason: string | undefined;
  await page.route('**/api/v1/admin/products**', async route => {
    if (route.request().method() === 'PATCH') {
      submittedReason = (route.request().postDataJSON() as { reason: string }).reason;
      await route.fulfill({
        status: 409,
        json: { success: false, data: null, error: { message: 'Product status changed concurrently' } },
      });
      return;
    }
    await route.fulfill({ json: response({ items: [{
      id: productId, vendorId, vendorName: 'Northstar Goods', name: 'Desk Lamp',
      slug: 'desk-lamp', price: 45, currency: 'TND', status: 'ACTIVE',
      createdAt: '2026-11-09T10:00:00Z', lastActorEmail: null, lastReason: null, lastActionAt: null,
    }], total: 1, limit: 20, offset: 0 }) });
  });

  await page.goto('/admin');
  page.on('dialog', async dialog => {
    if (dialog.type() === 'prompt') await dialog.accept('Policy review confirmed');
    else await dialog.accept();
  });
  await page.getByRole('button', { name: 'Deactivate' }).click();

  const products = page.locator('mh-admin-product-management');
  await expect(products.getByRole('alert')).toContainText('Product status changed concurrently');
  await expect(products.getByRole('row').filter({ hasText: 'Desk Lamp' })).toContainText('ACTIVE');
  expect(submittedReason).toBe('Policy review confirmed');
});

test('vendor application list recovers from an API outage using Retry', async ({ page }) => {
  await signInAsAdmin(page);
  await stubUnrelatedAdminApis(page);
  let getCount = 0;
  await page.route('**/api/v1/admin/vendor-applications**', async route => {
    if (route.request().method() !== 'GET') {
      await route.fulfill({ json: response(null) });
      return;
    }
    getCount += 1;
    if (getCount === 1) {
      await route.fulfill({
        status: 503,
        json: { success: false, data: null, error: { message: 'Application service temporarily unavailable' } },
      });
      return;
    }
    await route.fulfill({ json: response([{
      vendorId, ownerUserId: '66666666-6666-4666-8666-666666666666',
      displayName: 'Northstar Goods', status: 'PENDING',
      createdAt: '2026-11-08T10:00:00Z', rejectionReason: null,
    }]) });
  });

  await page.goto('/admin');
  const applications = page.locator('mh-admin-vendor-applications');
  await expect(applications.getByRole('alert')).toContainText('Application service temporarily unavailable');
  await applications.getByRole('button', { name: 'Retry' }).click();
  await expect(applications.getByText('Northstar Goods')).toBeVisible();
  await expect(applications.getByRole('alert')).toHaveCount(0);
  expect(getCount).toBe(2);
});

test('vendor reactivation conflict preserves the reason and selected vendor context', async ({ page }) => {
  await signInAsAdmin(page);
  await stubUnrelatedAdminApis(page);
  let submitted: Record<string, unknown> | undefined;
  await page.route('**/api/v1/admin/vendors**', async route => {
    if (route.request().method() === 'PATCH') {
      submitted = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        status: 409,
        json: { success: false, data: null, error: { message: 'Vendor state changed on another admin session' } },
      });
      return;
    }
    await route.fulfill({ json: response({ items: [{
      vendorId, ownerUserId: '66666666-6666-4666-8666-666666666666',
      ownerEmail: 'owner@example.test', displayName: 'Northstar Goods',
      status: 'SUSPENDED', createdAt: '2026-11-08T10:00:00Z',
      storeName: 'Northstar Store', storeSlug: 'northstar', supportEmail: null,
      lastActorEmail: 'admin@example.test', lastReason: 'Pending compliance review',
      lastActionAt: '2026-11-09T10:00:00Z',
    }], total: 1, limit: 20, offset: 0 }) });
  });

  await page.goto('/admin');
  const vendors = page.locator('mh-admin-vendor-management');
  await vendors.getByRole('button', { name: 'Reactivate vendor' }).click();
  await vendors.getByLabel('Reason').fill('Compliance review completed');
  await vendors.getByRole('button', { name: 'Confirm reactivation' }).click();

  await expect(vendors.locator('.vendor-dialog').getByRole('alert'))
    .toContainText('The vendor status changed before this action completed');
  await expect(vendors.getByRole('dialog')).toBeVisible();
  await expect(vendors.getByLabel('Reason')).toHaveValue('Compliance review completed');
  await expect(vendors.getByRole('dialog')).toContainText('Northstar Store');
  expect(submitted).toEqual({ reason: 'Compliance review completed' });
});
