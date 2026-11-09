import { expect, test } from '@playwright/test';

const vendorId = '55555555-5555-4555-8555-555555555555';

async function signInAs(page: import('@playwright/test').Page, role = 'ADMIN') {
  await page.addInitScript(({ role }) => {
    sessionStorage.setItem('markethub.access-token', 'e2e-admin-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: '33333333-3333-4333-8333-333333333333',
      email: 'e2e-admin@example.test',
      role
    }));
  }, { role });
}

function apiResponse<T>(data: T) {
  return { success: true, data, error: null, timestamp: new Date().toISOString() };
}

const application = {
  vendorId,
  ownerUserId: '66666666-6666-4666-8666-666666666666',
  displayName: 'Northstar Goods',
  status: 'PENDING',
  createdAt: '2026-11-08T10:00:00Z',
  rejectionReason: null
};

test('admin can approve a pending vendor application', async ({ page }) => {
  await signInAs(page);
  let approved = false;
  await page.route('**/api/v1/admin/vendor-applications**', async route => {
    const request = route.request();
    if (request.method() === 'GET') {
      await route.fulfill({ json: apiResponse(approved ? [] : [application]) });
      return;
    }
    expect(request.method()).toBe('POST');
    expect(new URL(request.url()).pathname).toBe(`/api/v1/admin/vendor-applications/${vendorId}/approve`);
    approved = true;
    await route.fulfill({ json: apiResponse({ ...application, status: 'APPROVED' }) });
  });

  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Vendor applications' })).toBeVisible();
  await expect(page.getByText('Northstar Goods')).toBeVisible();
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Approving this application activates the vendor role');
  await page.getByRole('button', { name: 'Confirm approval' }).click();
  await expect(page.getByRole('status')).toContainText('Northstar Goods approved');
  await expect(page.getByText('No pending vendor applications')).toBeVisible();
});

test('admin rejection requires a reason and records it with the request', async ({ page }) => {
  await signInAs(page);
  let submitted: Record<string, unknown> | undefined;
  await page.route('**/api/v1/admin/vendor-applications**', async route => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: apiResponse([application]) });
      return;
    }
    expect(new URL(route.request().url()).pathname).toBe(`/api/v1/admin/vendor-applications/${vendorId}/reject`);
    submitted = route.request().postDataJSON() as Record<string, unknown>;
    await route.fulfill({ json: apiResponse({ ...application, status: 'REJECTED', rejectionReason: 'Business details could not be verified' }) });
  });

  await page.goto('/admin');
  await page.getByRole('button', { name: 'Reject', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm rejection' }).click();
  await expect(page.locator('.application-dialog').getByRole('alert')).toContainText('Enter a rejection reason');
  expect(submitted).toBeUndefined();

  await page.getByLabel('Reason for rejection').fill('Business details could not be verified');
  await page.getByRole('button', { name: 'Confirm rejection' }).click();
  await expect(page.getByRole('status')).toContainText('Northstar Goods rejected');
  expect(submitted).toEqual({ reason: 'Business details could not be verified' });
});

test('vendor applications show a retry state when the API is unavailable', async ({ page }) => {
  await signInAs(page);
  await page.route('**/api/v1/admin/vendor-applications**', route => route.fulfill({ status: 503, json: { error: { message: 'Service unavailable' } } }));
  await page.goto('/admin');
  await expect(page.locator('.vendor-applications').getByRole('alert')).toContainText('Service unavailable');
  await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
});

test('non-admin users cannot access vendor application review', async ({ page }) => {
  await signInAs(page, 'CUSTOMER');
  await page.goto('/admin');
  await expect(page).toHaveURL('/');
});
