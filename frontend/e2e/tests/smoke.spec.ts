import { expect, test } from '@playwright/test';

test('home page renders the MarketHub application', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('body')).not.toBeEmpty();
  await expect(page.locator('mh-root')).toBeVisible();
  await expect(page.locator('a[href="/products"]').first()).toBeVisible();
});

test('catalogue route renders even when the API is unavailable', async ({ page }) => {
  await page.goto('/products');
  await expect(page.locator('mh-root')).toBeVisible();
  await expect(page.locator('body')).toContainText(/catalogue|products|could not load/i);
});

test('customer-only routes redirect anonymous visitors to login', async ({ page }) => {
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login(?:\?|$)/);
});

test('vendor portal redirects anonymous visitors to login', async ({ page }) => {
  await page.goto('/vendor');
  await expect(page).toHaveURL(/\/login(?:\?|$)/);
});

test('registration route is reachable', async ({ page }) => {
  await page.goto('/register');
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.locator('input[type="email"]')).toBeVisible();
  await expect(page.locator('input[type="password"]')).toBeVisible();
});
