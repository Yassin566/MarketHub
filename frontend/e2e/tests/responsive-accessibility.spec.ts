import { expect, test } from '@playwright/test';

test.describe('shared responsive and accessible shell', () => {
  for (const width of [320, 360, 390, 768]) {
    test(`keeps the storefront usable without page overflow at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/');
      await expect(page.getByRole('link', { name: 'MarketHub home' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Discover' })).toBeVisible();

      const dimensions = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        document: document.documentElement.scrollWidth,
      }));
      expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport);
    });
  }

  test('keeps catalogue layout usable on narrow phones and tablets', async ({ page }) => {
    await page.goto('/products');
    await expect(page.locator('.catalog-shell')).toBeVisible();

    for (const width of [320, 390, 768]) {
      await page.setViewportSize({ width, height: 844 });
      const dimensions = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        document: document.documentElement.scrollWidth,
      }));
      expect(dimensions.document, `document overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport);
    }
  });

  test('keeps login and registration forms usable at phone widths', async ({ page }) => {
    for (const route of ['/login', '/register']) {
      await page.goto(route);
      await expect(page.locator('.auth-layout')).toBeVisible();
      for (const width of [320, 360, 390]) {
        await page.setViewportSize({ width, height: 844 });
        const dimensions = await page.evaluate(() => ({
          viewport: document.documentElement.clientWidth,
          document: document.documentElement.scrollWidth,
        }));
        expect(dimensions.document, `${route} overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport);
        await expect(page.getByLabel('Email address')).toBeVisible();
        await expect(page.getByRole('textbox', { name: 'Password' })).toBeVisible();
      }
    }
  });

  test('keeps cart and checkout purchase panels within narrow viewports', async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('markethub.access-token', 'responsive-customer-token');
      sessionStorage.setItem('markethub.session-user', JSON.stringify({
        userId: 'customer-responsive', email: 'buyer@example.test', role: 'CUSTOMER'
      }));
    });
    const envelope = (data: unknown) => ({
      success: true, data, error: null, timestamp: '2026-11-09T10:00:00Z'
    });
    const cart = {
      id: 'cart-responsive', status: 'ACTIVE', version: 1,
      createdAt: '2026-11-09T09:00:00Z', updatedAt: '2026-11-09T09:00:00Z',
      items: [{
        productId: 'product-responsive', productName: 'A very long sample product name for narrow mobile layouts',
        quantity: 2, unitPrice: 15, currency: 'EUR', availableQuantity: 5,
        productStatus: 'ACTIVE', vendorStatus: 'APPROVED', updatedAt: '2026-11-09T09:00:00Z'
      }]
    };
    await page.route('**/api/v1/customer/cart/checkout/validate', route => route.fulfill({
      json: envelope({
        cartId: 'cart-responsive', valid: true,
        lines: [{
          productId: 'product-responsive',
          productName: 'A very long sample product name for narrow mobile layouts',
          quantity: 2, unitPrice: 15, currency: 'EUR', lineTotal: 30, eligible: true, issue: null
        }],
        subtotal: 30, currency: 'EUR', issues: []
      })
    }));
    await page.route('**/api/v1/customer/cart', route => route.fulfill({ json: envelope(cart) }));

    for (const route of ['/cart', '/checkout']) {
      await page.goto(route);
      await expect(page.locator('.customer-shell')).toBeVisible();
      for (const width of [320, 360, 390]) {
        await page.setViewportSize({ width, height: 844 });
        const dimensions = await page.evaluate(() => ({
          viewport: document.documentElement.clientWidth,
          document: document.documentElement.scrollWidth,
        }));
        expect(dimensions.document, `${route} overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewport);
      }
    }
  });

  test('provides a visible keyboard focus indicator for primary navigation', async ({ page }) => {
    await page.goto('/');
    const home = page.getByRole('link', { name: 'MarketHub home' });
    await home.focus();
    const outline = await home.evaluate(element => getComputedStyle(element).outlineStyle);
    expect(outline).toBe('solid');
  });

  test('retains a full navigation layout on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    const navigation = page.getByRole('navigation', { name: 'Main navigation' });
    await expect(navigation.getByRole('link', { name: 'Home' })).toBeVisible();
    await expect(navigation.getByRole('link', { name: 'Discover' })).toBeVisible();
    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      document: document.documentElement.scrollWidth,
    }));
    expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport);
  });
});
