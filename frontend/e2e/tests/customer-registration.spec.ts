import { expect, test } from '@playwright/test';

const envelope = <T>(data: T) => ({
  success: true,
  data,
  error: null,
  timestamp: '2026-11-15T18:00:00Z',
});

const registeredCustomer = {
  accessToken: 'registered-customer-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  userId: 'registered-customer-1',
  email: 'new.buyer@example.test',
  role: 'CUSTOMER',
};

test.describe('customer registration journey', () => {
  test('validates password length before submitting registration', async ({ page }) => {
    let registrationRequests = 0;
    await page.route('**/api/v1/auth/register', async route => {
      registrationRequests += 1;
      await route.fulfill({ json: envelope(registeredCustomer) });
    });

    await page.goto('/register');
    await page.getByLabel('Email address').fill('new.buyer@example.test');
    const passwordInput = page.getByRole('textbox', { name: 'Password' });
    await passwordInput.fill('short-pass');
    await passwordInput.blur();
    await expect(page.getByText('Password must contain at least 12 characters.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
    expect(registrationRequests).toBe(0);
  });

  test('creates a customer account, establishes a session and opens the account area', async ({ page }) => {
    let submittedBody: Record<string, unknown> | undefined;
    await page.route('**/api/v1/auth/register', async route => {
      submittedBody = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({ json: envelope(registeredCustomer) });
    });

    await page.goto('/register');
    await page.getByLabel('Email address').fill('new.buyer@example.test');
    await page.getByRole('textbox', { name: 'Password' }).fill('MarketHubBuyer2026!');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByText('new.buyer@example.test')).toBeVisible();
    expect(submittedBody).toEqual({
      email: 'new.buyer@example.test',
      password: 'MarketHubBuyer2026!',
    });
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('markethub.access-token')))
      .toBe('registered-customer-token');
    await expect.poll(() => page.evaluate(() => JSON.parse(
      sessionStorage.getItem('markethub.session-user') ?? 'null',
    ))).toEqual({
      userId: 'registered-customer-1',
      email: 'new.buyer@example.test',
      role: 'CUSTOMER',
    });
  });

  test('shows a registration API error without creating a session', async ({ page }) => {
    await page.route('**/api/v1/auth/register', route => route.fulfill({
      status: 409,
      json: {
        success: false,
        data: null,
        error: { message: 'An account with this email already exists.' },
      },
    }));

    await page.goto('/register');
    await page.getByLabel('Email address').fill('existing.buyer@example.test');
    await page.getByRole('textbox', { name: 'Password' }).fill('MarketHubBuyer2026!');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByRole('alert')).toContainText('An account with this email already exists.');
    await expect(page).toHaveURL(/\/register$/);
    expect(await page.evaluate(() => sessionStorage.getItem('markethub.access-token'))).toBeNull();
  });
});
