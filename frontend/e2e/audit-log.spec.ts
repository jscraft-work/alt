import { expect, test, type Page, type Route } from "@playwright/test";
import { mockBaseline } from "./utils/admin-mocks";

/**
 * F5 보너스 시나리오 5 — audit-log 페이지.
 */

const ROW_1 = {
  id: "dddddddd-0000-0000-0000-000000000001",
  occurredAt: "2026-05-29T13:00:00+09:00",
  actorType: "APP_USER",
  actorId: "01999999-0000-0000-0000-000000000001",
  targetType: "STRATEGY_INSTANCE",
  targetId: "0fdaf6a4-44e4-4ad0-9c11-9ec0c8a85f01",
  actionType: "LIFECYCLE_TRANSITION",
  beforeJson: '{"lifecycleState":"inactive"}',
  afterJson: '{"lifecycleState":"active"}',
  summaryJson: '{"reason":"D-day"}',
};

async function mockAuditLog(
  page: Page,
  opts?: { lastParams?: URLSearchParams[] },
): Promise<void> {
  await page.route("**/api/admin/audit-log**", async (route: Route) => {
    const url = new URL(route.request().url());
    opts?.lastParams?.push(url.searchParams);
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          rows: [ROW_1],
          page: 0,
          size: 50,
          totalElements: 1,
          totalPages: 1,
        },
      }),
    });
  });
}

test.describe("audit-log 화면 (F4 신규)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
  });

  test("진입 시 표 + 페이지 표시", async ({ page }) => {
    await mockAuditLog(page);
    await page.goto("/admin/audit-log");

    await expect(
      page.getByRole("heading", { name: "audit-log" }),
    ).toBeVisible();
    await expect(page.getByText("LIFECYCLE_TRANSITION")).toBeVisible();
    await expect(page.getByText(/총 1 건 · 페이지 1 \/ 1/)).toBeVisible();
  });

  test("필터 적용 → API 재호출", async ({ page }) => {
    const lastParams: URLSearchParams[] = [];
    await mockAuditLog(page, { lastParams });

    await page.goto("/admin/audit-log");
    await expect.poll(() => lastParams.length).toBeGreaterThanOrEqual(1);

    await page.getByPlaceholder(/STRATEGY_INSTANCE/).fill("STRATEGY_INSTANCE");
    await page.getByRole("button", { name: "필터 적용" }).click();
    await expect
      .poll(() => lastParams[lastParams.length - 1]?.get("targetType"))
      .toBe("STRATEGY_INSTANCE");
  });

  test("상세 dialog → before/after JSON 표시", async ({ page }) => {
    await mockAuditLog(page);
    await page.goto("/admin/audit-log");

    // Eye 버튼
    await page
      .getByRole("button", { name: "상세 보기" })
      .first()
      .click();

    await expect(page.getByText("audit_log 상세")).toBeVisible();
    // dialog 안 JsonBlock label 노출
    await expect(
      page.getByText("before", { exact: true }).last(),
    ).toBeVisible();
    await expect(page.getByText("after", { exact: true }).last()).toBeVisible();
    await expect(
      page.getByText("summary", { exact: true }).last(),
    ).toBeVisible();
    // summary JSON 은 표에 없는 텍스트 — dialog 에서만 노출
    await expect(page.getByText('"reason":"D-day"')).toBeVisible();
  });
});
