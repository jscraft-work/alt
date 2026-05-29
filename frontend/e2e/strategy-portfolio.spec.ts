import { expect, test } from "@playwright/test";
import {
  INSTANCE_ID,
  INSTANCE_NAME,
  mockBaseline,
  mockInstanceDashboard,
} from "./utils/admin-mocks";

/**
 * F5 보너스 시나리오 7 → F6 — portfolio 페이지.
 *
 * F6 변경: `/strategy/{id}/portfolio` → `/portfolio?instanceId={id}` 로 이동
 */

test.describe("portfolio 화면 (F6 — 글로벌 path)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
    await mockInstanceDashboard(page);
  });

  test("진입 시 4 KPI + 보유 종목 표 + 최근 주문 표", async ({ page }) => {
    await page.goto(`/portfolio?instanceId=${INSTANCE_ID}`);

    await expect(
      page.getByRole("heading", {
        name: new RegExp(`포트폴리오.*${INSTANCE_NAME}`),
      }),
    ).toBeVisible();

    // KPI 라벨
    await expect(page.getByText("총 자산")).toBeVisible();
    await expect(page.getByText("현금")).toBeVisible();
    await expect(page.getByText("오늘 실현손익")).toBeVisible();
    await expect(page.getByText("보유 종목").first()).toBeVisible();

    // mock 한 position symbolName
    await expect(page.getByText("삼성전자").first()).toBeVisible();

    // 최근 주문 — CardTitle 은 div 일 수 있어 getByText 로 대응
    await expect(page.getByText("최근 주문").first()).toBeVisible();
  });
});
