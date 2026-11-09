import { expect, test } from '@playwright/test';

const paymentId = '11111111-1111-4111-8111-111111111111';
const orderId = '22222222-2222-4222-8222-222222222222';

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

test('admin can review and confirm a provider payment outcome with an audit record', async ({ page }) => {
  await signInAs(page);
  let resolved = false;
  let submittedBody: Record<string, unknown> | undefined;

  await page.route('**/api/v1/admin/payments/reconciliation**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 20, offset: 0 }) })
  );
  await page.route('**/api/v1/admin/payments**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname.endsWith('/reconciliation')) {
      await route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 20, offset: 0 }) });
      return;
    }
    if (url.pathname.endsWith('/resolution') && request.method() === 'POST') {
      submittedBody = request.postDataJSON() as Record<string, unknown>;
      resolved = true;
      await route.fulfill({ json: apiResponse({
        id: '44444444-4444-4444-8444-444444444444',
        paymentId, actorUserId: '33333333-3333-4333-8333-333333333333',
        actorEmail: 'e2e-admin@example.test', decision: 'CONFIRM_SUCCEEDED',
        previousPaymentStatus: 'REQUIRES_REVIEW', resultingPaymentStatus: 'SUCCEEDED',
        evidenceReference: 'provider-txn-e2e-55',
        reason: 'Provider dashboard confirms capture',
        createdAt: '2026-11-09T10:00:00Z'
      }) });
      return;
    }
    if (request.method() === 'GET') {
      await route.fulfill({ json: apiResponse({
        items: resolved ? [] : [{
          paymentId, orderId, providerCode: 'SANDBOX',
          providerReference: 'sandbox-ref-55', status: 'REQUIRES_REVIEW',
          amount: 19.95, currency: 'USD',
          createdAt: '2026-11-09T09:00:00Z', updatedAt: '2026-11-09T09:30:00Z'
        }],
        total: resolved ? 0 : 1, limit: 100, offset: 0
      }) });
      return;
    }
    await route.continue();
  });

  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Payments requiring review' })).toBeVisible();
  await expect(page.getByText('sandbox-ref-55')).toBeVisible();
  await page.getByRole('button', { name: 'Review resolution' }).click();

  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByLabel('Provider evidence reference').fill('provider-txn-e2e-55');
  await page.getByLabel('Reason for this decision').fill('Provider dashboard confirms capture');
  await page.getByRole('button', { name: 'Review decision' }).click();
  await expect(page.getByText('Confirm this irreversible action')).toBeVisible();
  await page.getByRole('button', { name: 'Confirm and apply' }).click();

  await expect(page.getByRole('heading', { name: 'Resolution recorded' })).toBeVisible();
  await expect(page.getByText('e2e-admin@example.test')).toBeVisible();
  await expect(page.getByText('provider-txn-e2e-55')).toBeVisible();
  expect(submittedBody).toEqual({
    decision: 'CONFIRM_SUCCEEDED',
    evidenceReference: 'provider-txn-e2e-55',
    reason: 'Provider dashboard confirms capture'
  });
  expect(submittedBody).not.toHaveProperty('actorUserId');
});

test('admin resolution form validates evidence before showing confirmation', async ({ page }) => {
  await signInAs(page);
  let postCount = 0;
  await page.route('**/api/v1/admin/payments/reconciliation**', route =>
    route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 20, offset: 0 }) })
  );
  await page.route('**/api/v1/admin/payments**', async route => {
    if (new URL(route.request().url()).pathname.endsWith('/reconciliation')) {
      await route.fulfill({ json: apiResponse({ items: [], total: 0, limit: 20, offset: 0 }) });
      return;
    }
    if (route.request().method() === 'POST') postCount++;
    await route.fulfill({ json: apiResponse({
      items: [{
        paymentId, orderId, providerCode: 'SANDBOX', providerReference: null,
        status: 'REQUIRES_REVIEW', amount: 10, currency: 'USD',
        createdAt: '2026-11-09T09:00:00Z', updatedAt: '2026-11-09T09:30:00Z'
      }], total: 1, limit: 100, offset: 0
    }) });
  });

  await page.goto('/admin');
  await page.getByRole('button', { name: 'Review resolution' }).click();
  await page.getByRole('button', { name: 'Review decision' }).click();
  await expect(page.getByRole('alert')).toContainText('evidence reference');
  await expect(page.getByText('Confirm this irreversible action')).toHaveCount(0);
  expect(postCount).toBe(0);
});

test('non-admin users cannot access the admin resolution portal', async ({ page }) => {
  await signInAs(page, 'CUSTOMER');
  await page.goto('/admin');
  await expect(page).toHaveURL('/');
});
