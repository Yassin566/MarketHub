import { expect, test } from '@playwright/test';

const apiResponse = <T>(data: T) => ({
  success: true, data, error: null, timestamp: new Date().toISOString()
});

async function signInAsAdmin(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'e2e-admin-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: '33333333-3333-4333-8333-333333333333',
      email: 'admin@example.test',
      role: 'ADMIN'
    }));
  });
}

const product = (id: string, name: string) => ({
  id, vendorId: '66666666-6666-4666-8666-666666666666',
  vendorName: 'Northstar Goods', name, slug: name.toLowerCase().replaceAll(' ', '-'),
  price: 12.5, currency: 'USD', status: 'ACTIVE', createdAt: '2026-11-09T10:00:00Z',
  lastActorEmail: null, lastReason: null, lastActionAt: null
});

test('admin product search and pagination preserve API filters and offsets', async ({ page }) => {
  await signInAsAdmin(page);
  const requests: URL[] = [];
  await page.route('**/api/v1/admin/products**', async route => {
    const url = new URL(route.request().url());
    requests.push(url);
    const offset = Number(url.searchParams.get('offset') ?? 0);
    const items = offset === 20
      ? [product('55555555-5555-4555-8555-555555555556', 'Second page product')]
      : [product('55555555-5555-4555-8555-555555555555', 'First page product')];
    await route.fulfill({ json: apiResponse({ items, total: 21, limit: 20, offset }) });
  });
  await page.route('**/api/v1/admin/payments**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 100, offset: 0 }) }));
  await page.route('**/api/v1/admin/reviews**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 100, offset: 0 }) }));

  await page.goto('/admin');
  await expect(page.getByText('First page product')).toBeVisible();
  await page.getByLabel('Search products or vendors').fill('Northstar');
  await page.getByLabel('Product status').selectOption('ACTIVE');
  await page.locator('mh-admin-product-management').getByRole('button', { name: 'Search', exact: true }).click();
  await expect.poll(() => requests.at(-1)?.searchParams.get('search')).toBe('Northstar');
  expect(requests.at(-1)?.searchParams.get('status')).toBe('ACTIVE');
  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByText('Second page product')).toBeVisible();
  expect(requests.at(-1)?.searchParams.get('offset')).toBe('20');
  expect(requests.at(-1)?.searchParams.get('search')).toBe('Northstar');
  expect(requests.at(-1)?.searchParams.get('status')).toBe('ACTIVE');
});

test('admin vendor search resets pagination and displays a stale-status conflict', async ({ page }) => {
  await signInAsAdmin(page);
  const requests: URL[] = [];
  await page.route('**/api/v1/admin/vendors**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push(url);
    if (request.method() === 'PATCH') {
      await route.fulfill({
        status: 409,
        json: { success: false, data: null, error: { message: 'Vendor status changed concurrently' } }
      });
      return;
    }
    const offset = Number(url.searchParams.get('offset') ?? 0);
    const vendor = {
      vendorId: '77777777-7777-4777-8777-777777777777',
      ownerUserId: '66666666-6666-4666-8666-666666666666',
      ownerEmail: 'owner@example.test', displayName: 'Northstar Goods',
      status: 'APPROVED', createdAt: '2026-11-08T10:00:00Z',
      storeName: 'Northstar Store', storeSlug: 'northstar', supportEmail: null,
      lastActorEmail: null, lastReason: null, lastActionAt: null
    };
    await route.fulfill({ json: apiResponse({
      items: [vendor], total: 21, limit: 20, offset
    }) });
  });
  await page.route('**/api/v1/admin/payments**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 100, offset: 0 }) }));
  await page.route('**/api/v1/admin/reviews**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 100, offset: 0 }) }));

  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Vendor management' })).toBeVisible();
  await page.getByRole('button', { name: 'Next' }).click();
  await expect.poll(() => requests.at(-1)?.searchParams.get('offset')).toBe('20');
  await page.getByLabel('Search vendors').fill('owner@example.test');
  await page.locator('mh-admin-vendor-management').getByRole('button', { name: 'Search', exact: true }).click();
  await expect.poll(() => requests.at(-1)?.searchParams.get('offset')).toBe('0');
  expect(requests.at(-1)?.searchParams.get('search')).toBe('owner@example.test');

  await page.getByRole('button', { name: 'Suspend vendor' }).click();
  await page.getByLabel('Reason').fill('Policy review requires a fresh status check');
  await page.getByRole('button', { name: 'Confirm suspension' }).click();
  await expect(page.locator('.vendor-dialog').getByRole('alert'))
    .toContainText('The vendor status changed before this action completed');
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('admin application review conflict preserves the dialog and asks for refresh', async ({ page }) => {
  await signInAsAdmin(page);
  await page.route('**/api/v1/admin/vendor-applications**', async route => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: apiResponse([{
        vendorId: '55555555-5555-4555-8555-555555555555',
        ownerUserId: '66666666-6666-4666-8666-666666666666',
        displayName: 'Northstar Goods', status: 'PENDING',
        createdAt: '2026-11-08T10:00:00Z', rejectionReason: null
      }]) });
      return;
    }
    await route.fulfill({
      status: 409,
      json: { success: false, data: null, error: { message: 'Already reviewed' } }
    });
  });
  await page.route('**/api/v1/admin/products**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 20, offset: 0 }) }));
  await page.route('**/api/v1/admin/payments**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 100, offset: 0 }) }));
  await page.route('**/api/v1/admin/reviews**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 100, offset: 0 }) }));

  await page.goto('/admin');
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm approval' }).click();
  await expect(page.locator('.application-dialog').getByRole('alert'))
    .toContainText('This application has already been reviewed');
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog').getByText('Northstar Goods')).toBeVisible();
});
