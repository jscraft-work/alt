import type { Page, Route } from "@playwright/test";

/**
 * F5 e2e 공통 mock util.
 *
 * 모든 시나리오에 공통으로 필요한 인증 + 인스턴스 목록 mock.
 * 페이지별 특이 mock 은 각 spec 파일에서 추가.
 */

export const INSTANCE_ID = "0fdaf6a4-44e4-4ad0-9c11-9ec0c8a85f01";
export const INSTANCE_NAME = "박스 단타 v1";

export async function mockAuth(page: Page): Promise<void> {
  await page.route("**/api/auth/csrf", async (route: Route) => {
    await route.fulfill({
      status: 200,
      headers: { "set-cookie": "XSRF-TOKEN=test-token; Path=/" },
      contentType: "application/json",
      body: JSON.stringify({ data: { ok: true } }),
    });
  });
  await page.route("**/api/auth/me", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          id: "01999999-0000-0000-0000-000000000001",
          loginId: "admin",
          displayName: "운영자",
          roleCode: "ADMIN",
        },
      }),
    });
  });
}

export async function mockStrategyInstances(page: Page): Promise<void> {
  // GET /api/admin/strategy-instances — 인스턴스 목록 (Sidebar / 페이지 내 lookup)
  await page.route(
    "**/api/admin/strategy-instances*",
    async (route: Route) => {
      const url = route.request().url();
      // /api/admin/strategy-instances/{id} 같은 detail 호출은 mockStrategyInstances 가 처리하지 않음
      if (url.match(/\/strategy-instances\/[0-9a-f-]{8,}/)) {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: INSTANCE_ID,
              strategyTemplateId: "01999999-0000-0000-0000-000000000010",
              strategyTemplateName: "박스 단타 템플릿",
              name: INSTANCE_NAME,
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
            },
          ],
        }),
      });
    },
  );
}

/** dashboard instance summary (instance overview / portfolio 페이지가 사용) */
export async function mockInstanceDashboard(page: Page): Promise<void> {
  await page.route(
    "**/api/dashboard/instances/*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            instance: {
              id: INSTANCE_ID,
              name: INSTANCE_NAME,
              executionMode: "paper",
              lifecycleState: "active",
              autoPausedReason: null,
              budgetAmount: 10_000_000,
              brokerAccountMasked: null,
            },
            portfolio: {
              cashAmount: 8_500_000,
              totalAssetAmount: 10_120_000,
              realizedPnlToday: 120_000,
            },
            positions: [
              {
                symbolCode: "005930",
                symbolName: "삼성전자",
                quantity: 20,
                avgBuyPrice: 80_000,
                lastMarkPrice: 81_000,
                unrealizedPnl: 20_000,
              },
            ],
            systemStatus: [],
            latestDecision: {
              decisionLogId: "01999999-0000-0000-0000-000000000020",
              cycleStatus: "EXECUTE",
              summary: "박스 상단 돌파 — 매수 1주",
              cycleStartedAt: "2026-05-29T13:30:00+09:00",
            },
            recentOrders: [
              {
                tradeOrderId: "01999999-0000-0000-0000-000000000030",
                symbolCode: "005930",
                side: "BUY",
                orderStatus: "filled",
                requestedAt: "2026-05-29T13:30:05+09:00",
                requestedQuantity: 1,
                requestedPrice: 80_000,
              },
            ],
          },
        }),
      });
    },
  );
}

/** strategy-overview / system-status (Sidebar / Dashboard) */
export async function mockDashboardGlobals(page: Page): Promise<void> {
  await page.route(
    "**/api/dashboard/strategy-overview",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
  await page.route(
    "**/api/dashboard/system-status",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
}

/**
 * 공통 baseline — 모든 spec 의 beforeEach 에서 호출 추천.
 */
export async function mockBaseline(page: Page): Promise<void> {
  await mockAuth(page);
  await mockStrategyInstances(page);
  await mockDashboardGlobals(page);
}
