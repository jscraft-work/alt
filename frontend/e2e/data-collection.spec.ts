import { expect, test, type Page, type Route } from "@playwright/test";
import { mockBaseline } from "./utils/admin-mocks";

/**
 * F5 필수 시나리오 2 — data-collection 페이지.
 *
 * - /admin/data-collection 진입 → 4 SectionCard 표시
 * - 종목 코드 입력 → POST ws/subscribe 호출 + toast
 * - Trash 버튼 → DELETE ws/subscribe/{code} 호출 + toast
 */

const SUMMARY_BODY = {
  data: {
    evaluatedAt: "2026-05-29T13:00:00+09:00",
    ws: {
      status: "ok",
      connectionState: "CONNECTED",
      lastTickAt: "2026-05-29T12:59:50+09:00",
      lastConnectedAt: "2026-05-29T09:00:00+09:00",
      lastDisconnectedAt: null,
      subscribedCount: 5,
      maxSubscriptions: 41,
      adhocCount: 1,
      count24hOk: 120,
      count24hDown: 0,
      count24hLimitExceeded: 0,
      latestEvent: {
        id: "01999999-0000-0000-0000-0000000000aa",
        occurredAt: "2026-05-29T12:59:50+09:00",
        serviceName: "marketdata",
        eventType: "ws.subscription.reconcile",
        statusCode: "ok",
        message: null,
        payloadJson: null,
      },
    },
    rest: {
      status: "ok",
      lastSnapshotAt: "2026-05-29T12:58:00+09:00",
      lastMinuteBarAt: "2026-05-29T12:55:00+09:00",
    },
    content: {
      status: "delayed",
      lastNewsAt: "2026-05-29T08:30:00+09:00",
      lastDisclosureAt: "2026-05-28T18:00:00+09:00",
      newsCount24hOk: 50,
      newsCount24hDown: 0,
      disclosureCount24hOk: 10,
      disclosureCount24hDown: 0,
      latestEvent: null,
    },
    macro: {
      status: "ok",
      lastBaseDate: "2026-05-28",
      count24hOk: 1,
      count24hDown: 0,
      latestEvent: null,
    },
  },
};

const SUBSCRIPTIONS_BODY = {
  data: {
    rows: [
      { symbolCode: "005930", source: "watchlist" },
      { symbolCode: "000660", source: "watchlist" },
      { symbolCode: "035720", source: "adhoc" },
      { symbolCode: "035420", source: "adhoc_pending" },
    ],
    totalCount: 4,
    maxSubscriptions: 41,
    connectionState: "CONNECTED",
    lastTickAt: "2026-05-29T12:59:50+09:00",
  },
};

async function mockDataCollectionApis(
  page: Page,
  opts?: {
    subscribeBody?: (codes: string[]) => unknown;
    unsubscribeRecord?: { codes: string[] };
  },
): Promise<void> {
  await page.route(
    "**/api/admin/data-collection/summary",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SUMMARY_BODY),
      });
    },
  );
  await page.route(
    "**/api/admin/data-collection/ws/subscriptions",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SUBSCRIPTIONS_BODY),
      });
    },
  );
  await page.route(
    "**/api/admin/data-collection/ops-events**",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [] }),
      });
    },
  );
  await page.route(
    "**/api/admin/data-collection/ws/subscribe",
    async (route: Route) => {
      if (route.request().method() === "POST") {
        const postData = route.request().postDataJSON() as {
          symbolCodes: string[];
        };
        const responseBody =
          opts?.subscribeBody?.(postData.symbolCodes) ?? {
            data: {
              addedCount: postData.symbolCodes.length,
              requestedCount: postData.symbolCodes.length,
            },
          };
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(responseBody),
        });
        return;
      }
      await route.continue();
    },
  );
  await page.route(
    "**/api/admin/data-collection/ws/subscribe/*",
    async (route: Route) => {
      if (route.request().method() === "DELETE") {
        const url = new URL(route.request().url());
        const code = decodeURIComponent(
          url.pathname.split("/").pop() ?? "",
        );
        opts?.unsubscribeRecord?.codes.push(code);
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: { removed: true, symbolCode: code },
          }),
        });
        return;
      }
      await route.continue();
    },
  );
}

test.describe("data-collection 화면 (F2)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
  });

  test("진입 시 4 SectionCard 표시 + WS 구독 종목 표", async ({ page }) => {
    await mockDataCollectionApis(page);
    await page.goto("/admin/data-collection");

    await expect(
      page.getByRole("heading", { name: "데이터 수집" }),
    ).toBeVisible();

    // 4 섹션 타이틀
    await expect(page.getByText("KIS WebSocket (실시간)")).toBeVisible();
    await expect(page.getByText("KIS REST (시세)")).toBeVisible();
    await expect(page.getByText("DART + 네이버 (뉴스·공시)")).toBeVisible();
    // "yfinance 매크로" 는 SectionCard 와 ops_events 표 둘 다 사용
    await expect(page.getByText("yfinance 매크로").first()).toBeVisible();

    // WS 카드: 구독 5/41 표시
    await expect(page.getByText("5 / 41")).toBeVisible();

    // 종목 관리 표 4 row
    await expect(
      page.getByText(/총 4 \/ 41 종목/),
    ).toBeVisible();
    await expect(page.getByText("005930")).toBeVisible();
    await expect(page.getByText("035420")).toBeVisible();
  });

  test("종목 코드 입력 후 ad-hoc 추가 → POST + toast", async ({ page }) => {
    const postedCodes: string[][] = [];
    await mockDataCollectionApis(page, {
      subscribeBody: (codes) => {
        postedCodes.push(codes);
        return {
          data: { addedCount: codes.length, requestedCount: codes.length },
        };
      },
    });

    await page.goto("/admin/data-collection");

    const input = page.getByPlaceholder(/종목코드.*다중 입력/);
    await input.fill("005930, 000660 035420");
    await page.getByRole("button", { name: /ad-hoc 추가/ }).click();

    // POST 호출 확인
    await expect.poll(() => postedCodes.length).toBeGreaterThanOrEqual(1);
    expect(postedCodes[0]).toEqual(
      expect.arrayContaining(["005930", "000660", "035420"]),
    );

    // toast 표시 (sonner)
    await expect(
      page.getByText(/ad-hoc 구독 큐에 3\/3 개 종목 추가됨/),
    ).toBeVisible({ timeout: 5_000 });

    // input cleared
    await expect(input).toHaveValue("");
  });

  test("ad-hoc 종목 Trash 버튼 → DELETE + toast", async ({ page }) => {
    const removed = { codes: [] as string[] };
    await mockDataCollectionApis(page, { unsubscribeRecord: removed });

    await page.goto("/admin/data-collection");

    // 035720 ad-hoc row 의 trash 버튼
    const removeButton = page.getByRole("button", {
      name: "035720 구독 제거",
    });
    await expect(removeButton).toBeVisible();
    await removeButton.click();

    await expect.poll(() => removed.codes).toContain("035720");

    // 성공 toast
    await expect(
      page.getByText(/035720 ad-hoc 구독 제거/),
    ).toBeVisible({ timeout: 5_000 });
  });
});
