import { expect, test } from '@playwright/test';

const protectedRoutes = {
  customer: ['/cart', '/checkout', '/notifications', '/orders', '/orders/order-123', '/account'],
  vendor: ['/vendor'],
  admin: ['/admin'],
} as const;

const sessions = [
  { role: 'CUSTOMER', home: '/account', blocked: [...protectedRoutes.vendor, ...protectedRoutes.admin] },
  { role: 'VENDOR', home: '/vendor', blocked: [...protectedRoutes.customer, ...protectedRoutes.admin] },
  { role: 'ADMIN', home: '/admin', blocked: [...protectedRoutes.customer, ...protectedRoutes.vendor] },
  { role: 'SUPER_ADMIN', home: '/admin', blocked: [...protectedRoutes.customer, ...protectedRoutes.vendor] },
];

for (const session of sessions) {
  test.describe(`route access for ${session.role}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((role) => {
        sessionStorage.setItem('markethub.access-token', 'e2e-role-token');
        sessionStorage.setItem('markethub.session-user', JSON.stringify({
          userId: 'e2e-user', email: 'role@example.test', role,
        }));
      }, session.role);
    });

    for (const route of session.blocked) {
      test(`redirects away from ${route}`, async ({ page }) => {
        await page.goto(route);
        await expect(page).toHaveURL(session.home);
      });
    }
  });
}

test('unknown authenticated roles cannot enter protected workspaces', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'e2e-unknown-role-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'unknown-user', email: 'unknown@example.test', role: 'UNKNOWN',
    }));
  });

  await page.goto('/admin');
  await expect(page).toHaveURL('/');
});

test('anonymous visitors retain the requested customer destination', async ({ page }) => {
  await page.goto('/orders/order-123');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Forders%2Forder-123$/);
});

for (const session of sessions) {
  test(`${session.role} sessions retain access to the public catalogue`, async ({ page }) => {
    await page.addInitScript((role) => {
      sessionStorage.setItem('markethub.access-token', 'e2e-public-route-token');
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'e2e-user', email: 'role@example.test', role,
      }));
    }, session.role);

    await page.goto('/products');
    await expect(page.locator('.catalog-shell')).toBeVisible();
  });
}
