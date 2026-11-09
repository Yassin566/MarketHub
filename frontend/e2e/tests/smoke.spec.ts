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

test('customer review is restored after loading a product page and can be edited', async ({ page }) => {
  const productId = 'a4d9a77e-4c9b-4fb6-a21d-3c8d5e5f1042';
  const reviewId = 'b7d5b4ad-90d1-4d8c-9a0d-93a5ef62b3a1';
  const review = {
    id: reviewId, productId, rating: 4, reviewText: 'Good quality',
    createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z'
  };
  const response = (data: unknown) => ({ success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z' });

  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'test-customer-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'customer-123', email: 'customer@example.test', role: 'CUSTOMER'
    }));
  });
  await page.route('**/api/v1/products/sample-product', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({
      id: productId, vendorId: 'vendor-123', name: 'Sample product',
      slug: 'sample-product', price: 12.5, currency: 'EUR', createdAt: '2026-10-01T00:00:00Z'
    }))
  }));
  await page.route('**/api/v1/products/' + productId + '/reviews**', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({
      reviews: [review], limit: 10, offset: 0, hasMore: false,
      averageRating: 4, reviewCount: 1
    }))
  }));
  await page.route('**/api/v1/customer/products/' + productId + '/reviews/mine', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(response(review))
  }));

  await page.goto('/products/sample-product');
  await expect(page.getByText('Your submitted review')).toBeVisible();
  await page.getByRole('button', { name: 'Edit', exact: true }).click();
  await expect(page.getByLabel('Your review')).toHaveValue('Good quality');
  await page.getByLabel('Your review').fill('Updated after delivery');
  await page.route('**/api/v1/customer/reviews/' + reviewId, route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({ ...review, reviewText: 'Updated after delivery' }))
  }));
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Your review has been saved.')).toBeVisible();
});

test('anonymous visitors can read public product reviews without a customer review lookup', async ({ page }) => {
  const productId = 'a4d9a77e-4c9b-4fb6-a21d-3c8d5e5f1042';
  const response = (data: unknown) => ({ success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z' });
  await page.route('**/api/v1/products/sample-product', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({
      id: productId, vendorId: 'vendor-123', name: 'Sample product',
      slug: 'sample-product', price: 12.5, currency: 'EUR', createdAt: '2026-10-01T00:00:00Z'
    }))
  }));
  await page.route('**/api/v1/products/' + productId + '/reviews**', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({
      reviews: [], limit: 10, offset: 0, hasMore: false, averageRating: null, reviewCount: 0
    }))
  }));
  await page.goto('/products/sample-product');
  await expect(page.getByRole('heading', { name: 'Customer reviews' })).toBeVisible();
  await expect(page.getByText('No published reviews yet.')).toBeVisible();
});
