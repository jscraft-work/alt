import { expect, test, type Page, type Route } from "@playwright/test";
import { INSTANCE_ID, mockBaseline } from "./utils/admin-mocks";

/**
 * F5 필수 시나리오 4 — 라우트 redirect (F4 기존 path 호환).
 *
 * - /settings/instances/{id}/paper-eval → /strategy/{id}/paper-eval
 * - /settings/instances/{id}/watchlist → /strategy/{id}/watchlist
 * - /settings/instances/{id}/prompt-versions → /strategy/{id}/prompt
 * - /settings/instances/{id}/trade-history → /strategy/{id}/trade-history
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

  test("/settings/instances/{id}/paper-eval → /strategy/{id}/paper-eval", async ({
    page,
  }) => {
    await page.goto(`/settings/instances/${INSTANCE_ID}/paper-eval`);
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/paper-eval$`),
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

  test("/settings/instances/{id}/trade-history → /strategy/{id}/trade-history", async ({
    page,
  }) => {
    await page.goto(`/settings/instances/${INSTANCE_ID}/trade-history`);
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/trade-history$`),
    );
  });

  test("/strategy/{id} → /strategy/{id}/overview", async ({ page }) => {
    await page.goto(`/strategy/${INSTANCE_ID}`);
    await expect(page).toHaveURL(
      new RegExp(`/strategy/${INSTANCE_ID}/overview$`),
    );
  });
});
