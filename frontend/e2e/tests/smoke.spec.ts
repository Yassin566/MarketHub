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


test('catalogue cards show published review ratings and an unrated state', async ({ page }) => {
  const response = {
    success: true, error: null, timestamp: '2026-11-09T10:00:00Z',
    data: {
      content: [
        { id: 'rated-1', vendorId: 'vendor-1', name: 'Rated product', slug: 'rated-product',
          price: 19.99, currency: 'EUR', createdAt: '2026-11-01T00:00:00Z',
          averageRating: 4.5, reviewCount: 2 },
        { id: 'new-1', vendorId: 'vendor-2', name: 'New product', slug: 'new-product',
          price: 9.99, currency: 'EUR', createdAt: '2026-11-02T00:00:00Z',
          averageRating: null, reviewCount: 0 }
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 12, first: true, last: true
    }
  };
  await page.route('**/api/v1/products?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(response)
  }));
  await page.goto('/products');
  const ratedCard = page.locator('.product-card').filter({ hasText: 'Rated product' });
  const newCard = page.locator('.product-card').filter({ hasText: 'New product' });
  await expect(ratedCard.getByText('4.5', { exact: true })).toBeVisible();
  await expect(ratedCard.getByText('2 reviews')).toBeVisible();
  await expect(newCard.getByText('No reviews yet')).toBeVisible();
});

test('admin can filter reviews and publish or unpublish with an audit trail', async ({ page }) => {
  const review = {
    id: 'review-1', productId: 'product-1', productName: 'Sample product',
    rating: 2, reviewText: 'Arrived damaged', published: true,
    customerUserId: 'customer-1', createdAt: '2026-11-01T10:00:00Z',
    lastAction: null, lastActorEmail: null, lastActionAt: null
  };
  const response = (data: unknown) => ({success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z'});
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'test-admin-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'admin-1', email: 'admin@example.test', role: 'ADMIN'
    }));
  });
  await page.route('**/api/v1/admin/payments/reconciliation**', route =>
    route.fulfill({status: 200, contentType: 'application/json', body: JSON.stringify(response({items: [], total: 0, limit: 20, offset: 0}))}));
  await page.route('**/api/v1/admin/payments?**', route =>
    route.fulfill({status: 200, contentType: 'application/json', body: JSON.stringify(response({items: [], total: 0, limit: 100, offset: 0}))}));
  await page.route('**/api/v1/admin/reviews**', route =>
    route.fulfill({status: 200, contentType: 'application/json', body: JSON.stringify(response({items: [review], total: 1, limit: 100, offset: 0}))}));
  await page.route('**/api/v1/admin/reviews/review-1/publication', async route => {
    const body = route.request().postDataJSON();
    review.published = body.published;
    review.lastAction = body.published ? 'PUBLISHED' : 'UNPUBLISHED';
    review.lastActorEmail = 'admin@example.test';
    review.lastActionAt = '2026-11-09T10:30:00Z';
    await route.fulfill({status: 200, contentType: 'application/json', body: JSON.stringify(response(review))});
  });
  page.on('dialog', dialog => dialog.accept());
  await page.goto('/admin');
  await expect(page.getByRole('heading', {name: 'Product review moderation'})).toBeVisible();
  await expect(page.getByText('Arrived damaged')).toBeVisible();
  await page.getByRole('button', {name: 'Unpublish'}).click();
  await expect(page.getByText('Review unpublished. It is excluded from public reviews and ratings.')).toBeVisible();
  await expect(page.getByText('UNPUBLISHED by admin@example.test')).toBeVisible();
  await page.getByRole('button', {name: 'Publish'}).click();
  await expect(page.getByText('Review published. Public ratings have been updated.')).toBeVisible();
});


test('vendor can inspect inventory adjustment history and see the empty state', async ({ page }) => {
  const response = (data: unknown) => ({ success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z' });
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'test-vendor-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'vendor-123', email: 'vendor@example.test', role: 'VENDOR'
    }));
  });
  await page.route('**/api/v1/vendor/products', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(response([]))
  }));
  await page.route('**/api/v1/vendor/inventory', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response([{
      productId: 'product-1', productName: 'Desk Lamp', onHandQuantity: 8,
      reservedQuantity: 2, availableQuantity: 6, version: 3, updatedAt: '2026-11-08T10:00:00Z'
    }]))
  }));
  await page.route('**/api/v1/vendor/orders?**', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({ orders: [], limit: 20, offset: 0, hasMore: false }))
  }));
  await page.route('**/api/v1/vendor/analytics?**', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(response({ from: '2026-10-11', to: '2026-11-09', currencies: [], daily: [], topProducts: [] }))
  }));
  let historyRequests = 0;
  await page.route('**/api/v1/vendor/inventory/product-1/adjustments', route => {
    historyRequests += 1;
    return route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify(response([]))
    });
  });

  await page.goto('/vendor');
  await expect(page.getByRole('heading', { name: 'Your store, in motion.' })).toBeVisible();
  await page.getByRole('button', { name: /Inventory/ }).click();
  await expect(page.getByRole('cell', { name: 'Desk Lamp' })).toBeVisible();
  await page.getByRole('button', { name: 'View history' }).click();
  await expect(page.getByRole('heading', { name: 'Adjustment history · Desk Lamp' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'No adjustments recorded.' })).toBeVisible();
  expect(historyRequests).toBe(1);
});
