import { expect, test } from '@playwright/test';

const productId = 'a4d9a77e-4c9b-4fb6-a21d-3c8d5e5f1042';
const reviewId = 'b7d5b4ad-90d1-4d8c-9a0d-93a5ef62b3a1';
const envelope = <T>(data: T) => ({
  success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z',
});
const product = {
  id: productId, vendorId: 'vendor-123', name: 'Sample product',
  slug: 'sample-product', price: 12.5, currency: 'EUR',
  createdAt: '2026-10-01T00:00:00Z',
};
const review = {
  id: reviewId, productId, rating: 5, reviewText: 'Excellent quality',
  createdAt: '2026-11-09T10:00:00Z', updatedAt: '2026-11-09T10:00:00Z',
};

async function openProductAsCustomer(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('markethub.access-token', 'test-customer-token');
    sessionStorage.setItem('markethub.session-user', JSON.stringify({
      userId: 'customer-123', email: 'customer@example.test', role: 'CUSTOMER',
    }));
  });
  await page.route('**/api/v1/products/sample-product', route =>
    route.fulfill({ json: envelope(product) }),
  );
}

test.describe('customer product review create and delete lifecycle', () => {
  test('creates an eligible review and deletes it with confirmation', async ({ page }) => {
    await openProductAsCustomer(page);
    let ownedReview: typeof review | null = null;
    let publicReviews: typeof review[] = [];
    let createBody: Record<string, unknown> | undefined;
    let deleteCount = 0;

    await page.route(`**/api/v1/products/${productId}/reviews**`, route =>
      route.fulfill({ json: envelope({
        reviews: publicReviews, limit: 10, offset: 0, hasMore: false,
        averageRating: publicReviews.length ? 5 : null, reviewCount: publicReviews.length,
      }) }),
    );
    await page.route(`**/api/v1/customer/products/${productId}/reviews/mine`, route =>
      route.fulfill({ json: envelope(ownedReview) }),
    );
    await page.route(`**/api/v1/customer/products/${productId}/reviews`, async route => {
      if (route.request().method() === 'POST') {
        createBody = route.request().postDataJSON() as Record<string, unknown>;
        ownedReview = review;
        publicReviews = [review];
        await route.fulfill({ json: envelope(review) });
        return;
      }
      await route.continue();
    });
    await page.route(`**/api/v1/customer/reviews/${reviewId}`, async route => {
      if (route.request().method() === 'DELETE') {
        deleteCount += 1;
        ownedReview = null;
        publicReviews = [];
        await route.fulfill({ json: envelope(null) });
        return;
      }
      await route.continue();
    });

    await page.goto('/products/sample-product');
    await expect(page.getByRole('heading', { name: 'Customer reviews' })).toBeVisible();
    await expect(page.getByText('No published reviews yet.')).toBeVisible();
    await page.getByLabel('Rating').selectOption('5');
    await page.getByLabel('Your review').fill('Excellent quality');
    await page.getByRole('button', { name: 'Submit review' }).click();

    await expect(page.getByText('Your review has been saved.')).toBeVisible();
    await expect(page.getByText('Your submitted review')).toBeVisible();
    await expect(page.getByText('Excellent quality')).toBeVisible();
    expect(createBody).toEqual({ rating: 5, reviewText: 'Excellent quality' });

    page.on('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Delete', exact: true }).click();
    await expect(page.getByText('Your review was deleted.')).toBeVisible();
    await expect(page.getByText('Your submitted review')).toHaveCount(0);
    await expect(page.getByText('No published reviews yet.')).toBeVisible();
    expect(deleteCount).toBe(1);
  });

  test('explains that a fulfilled purchase is required when review submission is rejected', async ({ page }) => {
    await openProductAsCustomer(page);
    await page.route(`**/api/v1/products/${productId}/reviews**`, route =>
      route.fulfill({ json: envelope({
        reviews: [], limit: 10, offset: 0, hasMore: false,
        averageRating: null, reviewCount: 0,
      }) }),
    );
    await page.route(`**/api/v1/customer/products/${productId}/reviews/mine`, route =>
      route.fulfill({ json: envelope(null) }),
    );
    let postCount = 0;
    await page.route(`**/api/v1/customer/products/${productId}/reviews`, async route => {
      if (route.request().method() === 'POST') {
        postCount += 1;
        await route.fulfill({
          status: 403,
          json: { success: false, data: null, error: { message: 'Fulfilled purchase required' } },
        });
        return;
      }
      await route.continue();
    });

    await page.goto('/products/sample-product');
    await page.getByLabel('Your review').fill('Looks promising');
    await page.getByRole('button', { name: 'Submit review' }).click();
    await expect(page.getByRole('status')).toContainText(
      'A fulfilled order containing this product is required before reviewing.',
    );
    expect(postCount).toBe(1);
  });
});
