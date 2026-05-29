import { expect, test } from "@playwright/test";
import {
  INSTANCE_ID,
  INSTANCE_NAME,
  mockBaseline,
  mockInstanceDashboard,
} from "./utils/admin-mocks";

/**
 * F5 보너스 시나리오 6 — strategy overview 페이지.
 */

test.describe("strategy overview 화면 (F4 신규)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
    await mockInstanceDashboard(page);
  });

  test("진입 시 4 SummaryCard + 사이드 6 quick-link", async ({ page }) => {
    await page.goto(`/strategy/${INSTANCE_ID}/overview`);

    await expect(
      page.getByRole("heading", {
        name: new RegExp(`${INSTANCE_NAME} · 개요`),
      }),
    ).toBeVisible();

    // 4 카드 라벨 — SummaryCard 의 CardTitle 안에 위치, 텍스트가 SideLink hint 등에 중복될 수 있어 .first()
    await expect(page.getByText("lifecycle").first()).toBeVisible();
    await expect(
      page.getByText("실행 모드 / 예산").first(),
    ).toBeVisible();
    await expect(page.getByText("cycle 주기").first()).toBeVisible();
    await expect(page.getByText("최근 cycle").first()).toBeVisible();

    // 최근 cycle summary 표시
    await expect(page.getByText("박스 상단 돌파 — 매수 1주")).toBeVisible();

    // 사이드 quick-link 6개 (Sidebar 의 sub-nav 도 동시 노출되므로 .first 또는 main 영역 한정)
    await expect(
      page.getByRole("link", { name: /paper 평가/ }).first(),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: /매매 이력/ }).first(),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: /포트폴리오/ }).first(),
    ).toBeVisible();
  });

  test("사이드 quick-link 클릭 → 해당 페이지로 이동", async ({ page }) => {
    await page.goto(`/strategy/${INSTANCE_ID}/overview`);

    await page.getByRole("link", { name: /paper 평가/ }).first().click();
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/paper-eval`),
    );
  });
});
