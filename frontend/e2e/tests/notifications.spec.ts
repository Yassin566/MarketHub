import { expect, test } from '@playwright/test';

const notification = {
  id: 'notification-1', orderId: 'order-123', type: 'ORDER',
  title: 'Your order has shipped', message: 'The vendor dispatched your order.',
  createdAt: '2026-11-09T10:00:00Z', readAt: null,
};

test.describe('customer notification centre', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('markethub.access-token', 'test-customer-token');
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'customer-1', email: 'customer@example.test', role: 'CUSTOMER',
      }));
    });
  });

  test('shows inbox, unread badge, order link and mark-as-read action', async ({ page }) => {
    await page.route('**/api/v1/customer/notifications**', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: { success: true, data: {
          notifications: [notification], limit: 20, offset: 0, hasMore: false, unreadCount: 1,
        }, error: null, timestamp: new Date().toISOString() } });
      } else if (route.request().method() === 'PATCH') {
        await route.fulfill({ json: { success: true, data: {
          ...notification, readAt: '2026-11-09T11:00:00Z',
        }, error: null, timestamp: new Date().toISOString() } });
      }
    });
    await page.goto('/notifications');
    await expect(page.getByRole('heading', { name: 'Notifications.' })).toBeVisible();
    await expect(page.getByRole('link', { name: /Notifications/ })).toContainText('1');
    await expect(page.getByText('Your order has shipped')).toBeVisible();
    await expect(page.getByRole('link', { name: 'View order' })).toHaveAttribute('href', '/orders/order-123');
    await page.getByRole('button', { name: 'Mark read' }).click();
    await expect(page.getByRole('button', { name: 'Mark read' })).toHaveCount(0);
  });

  test('filters unread notifications and marks all read', async ({ page }) => {
    let unreadOnly = false;
    await page.route('**/api/v1/customer/notifications**', async route => {
      if (route.request().method() === 'GET') {
        unreadOnly = new URL(route.request().url()).searchParams.get('unreadOnly') === 'true';
        await route.fulfill({ json: { success: true, data: {
          notifications: unreadOnly ? [notification] : [notification],
          limit: 20, offset: 0, hasMore: false, unreadCount: unreadOnly ? 1 : 1,
        }, error: null, timestamp: new Date().toISOString() } });
      } else {
        await route.fulfill({ json: { success: true, data: 1, error: null, timestamp: new Date().toISOString() } });
      }
    });
    await page.goto('/notifications');
    await page.getByRole('button', { name: /Unread/ }).click();
    await expect.poll(() => unreadOnly).toBe(true);
    await page.getByRole('button', { name: 'Mark all as read' }).click();
    await expect(page.getByRole('button', { name: 'Mark all as read' })).toBeDisabled();
  });

  test('redirects non-customer sessions away from notification centre', async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'vendor-1', email: 'vendor@example.test', role: 'VENDOR',
      }));
    });
    await page.goto('/notifications');
    await expect(page).toHaveURL('/');
  });
});
