import { expect, test, type Page, type Route } from "@playwright/test";
import {
  INSTANCE_ID,
  INSTANCE_NAME,
  mockBaseline,
  mockInstanceDashboard,
} from "./utils/admin-mocks";

/**
 * F6 신규 시나리오 — 페이지 내부 인스턴스 selector 동작.
 *
 * - 글로벌 페이지 (/paper-eval, /trade-history, /portfolio) 에서 selector 가 보인다
 * - selector 변경 시 URL `?instanceId=` 가 업데이트된다
 * - 별도 인스턴스로 변경하면 새 instanceId 로 API 가 재호출된다
 *
 * 두 인스턴스 (INSTANCE_ID + SECOND_ID) 를 모의해서 selector 변경을 검증.
 */

const SECOND_INSTANCE_ID = "0fdaf6a4-44e4-4ad0-9c11-9ec0c8a85f02";
const SECOND_INSTANCE_NAME = "검증용 보조 인스턴스";

async function mockTwoInstances(page: Page): Promise<void> {
  // 기존 mockStrategyInstances 를 override — 2개 인스턴스 반환
  await page.route(
    "**/api/admin/strategy-instances*",
    async (route: Route) => {
      const url = route.request().url();
      if (url.match(/\/strategy-instances\/[0-9a-f-]{8,}/)) {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            buildInstance(INSTANCE_ID, INSTANCE_NAME),
            buildInstance(SECOND_INSTANCE_ID, SECOND_INSTANCE_NAME),
          ],
        }),
      });
    },
  );
}

function buildInstance(id: string, name: string) {
  return {
    id,
    strategyTemplateId: "01999999-0000-0000-0000-000000000010",
    strategyTemplateName: "박스 단타 템플릿",
    name,
    executionMode: "paper",
    lifecycleState: "active",
    autoPausedReason: null,
    brokerAccountId: null,
    brokerAccountMasked: null,
    budgetAmount: 10_000_000,
    currentPromptVersionId: null,
    tradingModelProfileId: null,
    cycleMinutes: 5,
    effectiveCycleMinutes: 5,
    executionConfigOverride: null,
    autoPausedAt: null,
    version: 1,
    updatedAt: "2026-05-29T01:00:00+09:00",
  };
}

async function mockPaperEvalForAllInstances(
  page: Page,
  capturedIds: string[],
): Promise<void> {
  // /api/admin/paper-eval/{id} (+ /series, /recent-matches)
  await page.route(
    /\/api\/admin\/paper-eval\/[0-9a-f-]{8,}(\/[a-z-]+)?(\?|$)/,
    async (route: Route) => {
      const url = new URL(route.request().url());
      const match = url.pathname.match(
        /\/paper-eval\/([0-9a-f-]{8,})/,
      );
      const id = match?.[1] ?? "";
      capturedIds.push(`${id}|${url.pathname}`);
      if (url.pathname.endsWith("/series")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: [] }),
        });
        return;
      }
      if (url.pathname.endsWith("/recent-matches")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: [] }),
        });
        return;
      }
      if (url.pathname.endsWith("/trade-history")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: {
              rows: [],
              page: 0,
              size: 50,
              totalElements: 0,
              totalPages: 0,
              summary: {
                tradesCount: 0,
                winCount: 0,
                lossCount: 0,
                sumNetPnlPct: 0,
              },
            },
          }),
        });
        return;
      }
      // snapshot
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            tradesCount: 0,
            wins: 0,
            losses: 0,
            hitRate: 0,
            sumProfitPct: 0,
            sumLossPct: 0,
            ev: 0,
            pf: null,
            mdd: 0,
            avgSlippageBuyPct: null,
            avgSlippageSellPct: null,
            avgSellTaxPct: null,
            avgFeePct: null,
            avgCostTotalPct: 0,
          },
        }),
      });
    },
  );
}

test.describe("F6 — 페이지 내부 인스턴스 selector", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
    await mockTwoInstances(page);
    await mockInstanceDashboard(page);
  });

  test("paper-eval 페이지 — selector 변경 시 URL + API instanceId 갱신", async ({
    page,
  }) => {
    const captured: string[] = [];
    await mockPaperEvalForAllInstances(page, captured);

    await page.goto(`/paper-eval?instanceId=${INSTANCE_ID}`);

    // URL 이 그대로 유지
    await expect(page).toHaveURL(
      new RegExp(`/paper-eval\\?instanceId=${INSTANCE_ID}`),
    );

    // 초기 API 호출에 첫 인스턴스 ID 가 사용됨
    await expect
      .poll(() => captured.some((row) => row.includes(INSTANCE_ID)))
      .toBe(true);

    // selector 변경 → 두 번째 인스턴스 선택
    await page
      .getByLabel("전략 인스턴스 선택")
      .selectOption({ label: SECOND_INSTANCE_NAME });

    // URL 의 instanceId 가 두 번째 인스턴스로 업데이트됨
    await expect(page).toHaveURL(
      new RegExp(`/paper-eval\\?instanceId=${SECOND_INSTANCE_ID}`),
    );

    // 두 번째 인스턴스 ID 로 API 가 재호출됨
    await expect
      .poll(() => captured.some((row) => row.includes(SECOND_INSTANCE_ID)))
      .toBe(true);
  });

  test("portfolio 페이지 — 초기 selector 가 URL instanceId 와 일치", async ({
    page,
  }) => {
    await page.goto(`/portfolio?instanceId=${INSTANCE_ID}`);

    await expect(page).toHaveURL(
      new RegExp(`/portfolio\\?instanceId=${INSTANCE_ID}`),
    );

    // 페이지 제목에 인스턴스 이름이 표시됨
    await expect(
      page.getByRole("heading", {
        name: new RegExp(`포트폴리오.*${INSTANCE_NAME}`),
      }),
    ).toBeVisible();
  });
});
