import { expect, test, type Page, type Route } from "@playwright/test";
import { INSTANCE_ID, mockBaseline } from "./utils/admin-mocks";

/**
 * F5 → F6 시나리오 4 — 라우트 redirect (F0/F4 기존 path 호환).
 *
 * F6 변경: 조회 메뉴 (paper-eval / trade-history / portfolio / trades) 는 글로벌 path 로 이동.
 * 따라서 기존 `/strategy/{id}/<sub>` 와 `/settings/instances/{id}/<sub>` 모두
 * 글로벌 path + `?instanceId=:id` 로 redirect 한다.
 *
 * - /strategy/{id}/paper-eval → /paper-eval?instanceId={id}
 * - /strategy/{id}/trade-history → /trade-history?instanceId={id}
 * - /strategy/{id}/portfolio → /portfolio?instanceId={id}
 * - /strategy/{id}/trades → /trades?instanceId={id}
 * - /settings/instances/{id}/paper-eval → /paper-eval?instanceId={id}
 * - /settings/instances/{id}/trade-history → /trade-history?instanceId={id}
 * - /settings/instances/{id}/watchlist → /strategy/{id}/watchlist (전략 관리에 남음)
 * - /settings/instances/{id}/prompt-versions → /strategy/{id}/prompt (전략 관리에 남음)
 * - /settings/data-collection → /admin/data-collection
 * - /settings/assets → /admin/asset-master
 * - /settings/models → /admin/llm-models
 * - /settings/system-parameters → /admin/system-parameters
 * - /settings → /strategy
 * - /settings/instances → /strategy
 */

async function mockMinimalApis(page: Page): Promise<void> {
  // 페이지가 진입은 하지만 데이터는 빈 응답
  await page.route("**/api/admin/paper-eval/**", async (route: Route) => {
    const url = new URL(route.request().url());
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
  });
  await page.route(
    "**/api/admin/data-collection/**",
    async (route: Route) => {
      const url = route.request().url();
      if (url.includes("/summary")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: {
              evaluatedAt: "2026-05-29T13:00:00+09:00",
              ws: {
                status: "ok",
                connectionState: "CONNECTED",
                lastTickAt: null,
                lastConnectedAt: null,
                lastDisconnectedAt: null,
                subscribedCount: 0,
                maxSubscriptions: 41,
                adhocCount: 0,
                count24hOk: 0,
                count24hDown: 0,
                count24hLimitExceeded: 0,
                latestEvent: null,
              },
              rest: {
                status: "unknown",
                lastSnapshotAt: null,
                lastMinuteBarAt: null,
              },
              content: {
                status: "unknown",
                lastNewsAt: null,
                lastDisclosureAt: null,
                newsCount24hOk: 0,
                newsCount24hDown: 0,
                disclosureCount24hOk: 0,
                disclosureCount24hDown: 0,
                latestEvent: null,
              },
              macro: {
                status: "unknown",
                lastBaseDate: null,
                count24hOk: 0,
                count24hDown: 0,
                latestEvent: null,
              },
            },
          }),
        });
        return;
      }
      if (url.includes("/subscriptions")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: {
              rows: [],
              totalCount: 0,
              maxSubscriptions: 41,
              connectionState: "UNKNOWN",
              lastTickAt: null,
            },
          }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
  await page.route(
    "**/api/admin/asset-masters**",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
  await page.route("**/api/admin/assets**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [] }),
    });
  });
  await page.route("**/api/admin/model-profiles**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [] }),
    });
  });
  await page.route(
    "**/api/admin/system-parameters**",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
  await page.route(
    `**/api/admin/strategy-instances/${INSTANCE_ID}/watchlist**`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
  await page.route(
    `**/api/admin/strategy-instances/${INSTANCE_ID}/prompt-versions**`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );

  // F6 — 글로벌 path 페이지가 redirect 후 마운트되면서 데이터 페치가 발생할 수 있다.
  // 빈 응답만 돌려주면 URL assertion 까지 안전하게 도달.
  await page.route("**/api/dashboard/instances/**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          instance: {
            id: INSTANCE_ID,
            name: "박스 단타 v1",
            executionMode: "paper",
            lifecycleState: "active",
            autoPausedReason: null,
            budgetAmount: 0,
            brokerAccountMasked: null,
          },
          portfolio: {
            cashAmount: 0,
            totalAssetAmount: 0,
            realizedPnlToday: 0,
          },
          positions: [],
          systemStatus: [],
          latestDecision: null,
          recentOrders: [],
        },
      }),
    });
  });
  await page.route("**/api/admin/trade-orders**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [],
        meta: { page: 1, size: 20, totalElements: 0, totalPages: 0 },
      }),
    });
  });
  await page.route("**/api/admin/trade-decisions**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [],
        meta: { page: 1, size: 20, totalElements: 0, totalPages: 0 },
      }),
    });
  });
}

test.describe("라우트 redirect (F4 호환)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
    await mockMinimalApis(page);
  });

  test("/settings → /strategy", async ({ page }) => {
    await page.goto("/settings");
    await expect(page).toHaveURL(/\/strategy$/);
  });

  test("/settings/instances → /strategy", async ({ page }) => {
    await page.goto("/settings/instances");
    await expect(page).toHaveURL(/\/strategy$/);
  });

  test("/settings/data-collection → /admin/data-collection", async ({
    page,
  }) => {
    await page.goto("/settings/data-collection");
    await expect(page).toHaveURL(/\/admin\/data-collection$/);
  });

  test("/settings/assets → /admin/asset-master", async ({ page }) => {
    await page.goto("/settings/assets");
    await expect(page).toHaveURL(/\/admin\/asset-master$/);
  });

  test("/settings/models → /admin/llm-models", async ({ page }) => {
    await page.goto("/settings/models");
    await expect(page).toHaveURL(/\/admin\/llm-models$/);
  });

  test("/settings/system-parameters → /admin/system-parameters", async ({
    page,
  }) => {
    await page.goto("/settings/system-parameters");
    await expect(page).toHaveURL(/\/admin\/system-parameters$/);
  });

  test("/settings/instances/{id}/paper-eval → /paper-eval?instanceId={id}", async ({
    page,
  }) => {
    await page.goto(`/settings/instances/${INSTANCE_ID}/paper-eval`);
    await expect(page).toHaveURL(
      new RegExp(`/paper-eval\\?instanceId=${INSTANCE_ID}$`),
    );
  });

  test("/settings/instances/{id}/watchlist → /strategy/{id}/watchlist", async ({
    page,
  }) => {
    await page.goto(`/settings/instances/${INSTANCE_ID}/watchlist`);
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/watchlist$`),
    );
  });

  test("/settings/instances/{id}/prompt-versions → /strategy/{id}/prompt", async ({
    page,
  }) => {
    await page.goto(`/settings/instances/${INSTANCE_ID}/prompt-versions`);
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/prompt$`),
    );
  });

  test("/settings/instances/{id}/trade-history → /trade-history?instanceId={id}", async ({
    page,
  }) => {
    await page.goto(`/settings/instances/${INSTANCE_ID}/trade-history`);
    await expect(page).toHaveURL(
      new RegExp(`/trade-history\\?instanceId=${INSTANCE_ID}$`),
    );
  });

  test("/strategy/{id} → /strategy/{id}/overview", async ({ page }) => {
    await page.goto(`/strategy/${INSTANCE_ID}`);
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/overview$`),
    );
  });

  // F6 신규 — 조회 메뉴 4종 의 strategy path 도 글로벌 path 로 redirect.
  test("/strategy/{id}/paper-eval → /paper-eval?instanceId={id}", async ({
    page,
  }) => {
    await page.goto(`/strategy/${INSTANCE_ID}/paper-eval`);
    await expect(page).toHaveURL(
      new RegExp(`/paper-eval\\?instanceId=${INSTANCE_ID}$`),
    );
  });

  test("/strategy/{id}/trade-history → /trade-history?instanceId={id}", async ({
    page,
  }) => {
    await page.goto(`/strategy/${INSTANCE_ID}/trade-history`);
    await expect(page).toHaveURL(
      new RegExp(`/trade-history\\?instanceId=${INSTANCE_ID}$`),
    );
  });

  test("/strategy/{id}/portfolio → /portfolio?instanceId={id}", async ({
    page,
  }) => {
    await page.goto(`/strategy/${INSTANCE_ID}/portfolio`);
    await expect(page).toHaveURL(
      new RegExp(`/portfolio\\?instanceId=${INSTANCE_ID}$`),
    );
  });

  test("/strategy/{id}/trades → /trades?instanceId={id}", async ({
    page,
  }) => {
    await page.goto(`/strategy/${INSTANCE_ID}/trades`);
    await expect(page).toHaveURL(
      new RegExp(`/trades\\?instanceId=${INSTANCE_ID}$`),
    );
  });
});
