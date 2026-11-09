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
