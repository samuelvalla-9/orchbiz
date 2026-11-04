import { expect, test } from '@playwright/test';
import * as axe from 'axe-core';

test('renders the fictional public site and booking fallback without overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('A fictional learning studio demo');
  await expect(page.getByRole('heading', { name: 'Foundation learning (demo)' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Open booking page' })).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth))
    .toBeLessThanOrEqual(390);
});

test('rejects invalid form values in the browser without posting', async ({ page }) => {
  let postCount = 0;
  await page.route('**/api/leads', async (route) => {
    postCount += 1;
    await route.fulfill({ status: 201, json: { leadId: 'unexpected' } });
  });
  await page.goto('/');

  await page.getByRole('button', { name: 'Send enquiry' }).click();
  await expect(page.getByText('Your name is required.')).toBeVisible();
  await page.getByLabel('Your name').fill('Sam Sample');
  await page.getByLabel('Email address').fill('not-an-email');
  await page.getByLabel('Phone number').fill('5550100');
  await page.getByLabel('What would you like to ask about?').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Send enquiry' }).click();

  await expect(page.getByText('Enter a valid email address.')).toBeVisible();
  expect(postCount).toBe(0);
});

test('submits the documented payload and reports success', async ({ page }) => {
  let submittedPayload: Record<string, unknown> | undefined;
  await page.route('**/api/leads', async (route) => {
    submittedPayload = route.request().postDataJSON() as Record<string, unknown>;
    await route.fulfill({ status: 201, json: { leadId: 'fictional-test-lead', duplicate: false } });
  });
  await page.goto('/');
  await page.getByLabel('Your name').fill('Sam Sample');
  await page.getByLabel('Email address').fill('sam@example.test');
  await page.getByLabel('Phone number').fill('5550100');
  await page.getByLabel('What would you like to ask about?').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Send enquiry' }).click();

  await expect(page.getByRole('status')).toContainText('demonstration enquiry has been received');
  expect(submittedPayload).toMatchObject({
    tenantId: 'example-learning-studio',
    sourcePage: '/',
    fields: {
      full_name: 'Sam Sample',
      email: 'sam@example.test',
      phone: '5550100',
      course_interest: 'Foundation learning (fictional demo)',
      _hp: ''
    }
  });
  expect(submittedPayload?.submittedAt).toEqual(expect.stringMatching(/^\d{4}-\d{2}-\d{2}T/));
  await expect(page.getByLabel('Your name')).toHaveValue('');
});

test('shows API rejection without claiming success', async ({ page }) => {
  await page.route('**/api/leads', (route) =>
    route.fulfill({ status: 429, json: { error: 'rate_limited' } }));
  await page.goto('/');
  await page.getByLabel('Your name').fill('Sam Sample');
  await page.getByLabel('Email address').fill('sam@example.test');
  await page.getByLabel('Phone number').fill('5550100');
  await page.getByLabel('What would you like to ask about?').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Send enquiry' }).click();

  await expect(page.getByRole('alert')).toContainText('too many attempts');
  await expect(page.getByRole('status')).toHaveCount(0);
});

test('has no axe WCAG 2.1 A/AA violations', async ({ page }) => {
  await page.goto('/');
  await page.addScriptTag({ content: axe.source });
  const results = await page.evaluate(async () => {
    const axeRuntime = (window as Window & { axe: { run: (context: Document, options: object) => Promise<{ violations: { id: string; help: string }[] }> } }).axe;
    return axeRuntime.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
  });

  expect(results.violations).toEqual([]);
});
