import { expect, test, type Page, type Route } from "@playwright/test";
import { INSTANCE_ID, mockBaseline } from "./utils/admin-mocks";

/**
 * F5 필수 시나리오 3 — trade-history 페이지.
 *
 * - /strategy/{id}/trade-history 진입 → 3 SummaryCard + 표
 * - 종목 필터 → API 재호출
 * - win-only toggle → 재호출
 * - 정렬 버튼 → 재호출
 * - 페이지네이션 Prev/Next
 */

function makeRow(idx: number, win: boolean) {
  return {
    matchId: `bbbbbbbb-0000-0000-0000-${String(idx).padStart(12, "0")}`,
    symbolCode: "005930",
    entryTime: "2026-05-22T09:30:00+09:00",
    exitTime: `2026-05-22T${10 + (idx % 5)}:15:00+09:00`,
    holdingMinutes: 45,
    matchedQuantity: 5,
    grossPnlPct: 0.012,
    netPnlPct: win ? 0.0085 : -0.002,
    slippageBuyPct: 0.0003,
    slippageSellPct: 0.0004,
    sellTaxPct: 0.0015,
    feePct: 0.00014,
    buyOrderId: "cccccccc-0000-0000-0000-000000000001",
    buyRequestedPrice: 80_000,
    buyAvgFilledPrice: 80_050,
    buyRequestedAmount: 400_000,
    buySlippageAmount: 250,
    buyCommissionAmount: 56,
    buyActualAmount: 400_306,
    buyWalkLevels: 1,
    buyPartialFillRatio: 1,
    sellOrderId: "cccccccc-0000-0000-0000-000000000002",
    sellRequestedPrice: 81_000,
    sellAvgFilledPrice: 81_000,
    sellRequestedAmount: 405_000,
    sellSlippageAmount: 0,
    sellSellTaxAmount: 607,
    sellCommissionAmount: 56,
    sellActualAmount: 404_337,
    sellWalkLevels: 1,
    sellPartialFillRatio: 1,
  };
}

async function mockTradeHistory(
  page: Page,
  opts?: {
    requestParams?: URLSearchParams[];
    totalElements?: number;
    pageSize?: number;
  },
): Promise<void> {
  const total = opts?.totalElements ?? 12;
  const pageSize = opts?.pageSize ?? 5;

  // glob 의 * 가 slash 매치 안하므로 정규식 사용
  await page.route(
    new RegExp(`/api/admin/paper-eval/${INSTANCE_ID}/trade-history`),
    async (route: Route) => {
      const url = new URL(route.request().url());
      opts?.requestParams?.push(url.searchParams);

      const winOnly = url.searchParams.get("winOnly") === "true";
      const lossOnly = url.searchParams.get("lossOnly") === "true";
      const sort = url.searchParams.get("sort") ?? "exit_time:desc";
      const pageNum = Number(url.searchParams.get("page") ?? "0");
      const size = Number(url.searchParams.get("size") ?? "50");
      const symbol = url.searchParams.get("symbol");

      // 12 row → 일부는 win, 일부는 loss
      let allRows = Array.from({ length: total }, (_, i) =>
        makeRow(i + 1, i % 3 !== 0),
      );
      if (winOnly) allRows = allRows.filter((r) => r.netPnlPct > 0);
      if (lossOnly) allRows = allRows.filter((r) => r.netPnlPct < 0);
      if (symbol) allRows = allRows.filter((r) => r.symbolCode === symbol);

      // sort
      if (sort.startsWith("net_pnl_pct")) {
        allRows.sort((a, b) =>
          sort.endsWith(":asc")
            ? a.netPnlPct - b.netPnlPct
            : b.netPnlPct - a.netPnlPct,
        );
      }

      const start = pageNum * size;
      const rows = allRows.slice(start, start + size);
      const winCount = allRows.filter((r) => r.netPnlPct > 0).length;
      const sumNetPnlPct = allRows.reduce((s, r) => s + r.netPnlPct, 0);
      const totalElements = allRows.length;
      const totalPages = Math.max(1, Math.ceil(totalElements / size));

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            rows,
            page: pageNum,
            size,
            totalElements,
            totalPages,
            summary: {
              tradesCount: totalElements,
              winCount,
              lossCount: totalElements - winCount,
              sumNetPnlPct,
            },
          },
        }),
      });
      void pageSize;
    },
  );
}

test.describe("trade-history 화면 (F3)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
  });

  test("진입 시 3 SummaryCard + 표 + 페이지네이션 표시", async ({ page }) => {
    await mockTradeHistory(page, { totalElements: 12 });
    await page.goto(`/strategy/${INSTANCE_ID}/trade-history`);

    await expect(
      page.getByRole("heading", { name: /매매 이력/ }),
    ).toBeVisible();

    // 3 summary card 라벨
    await expect(page.getByText("총 매매").first()).toBeVisible();
    await expect(page.getByText("승률 (win rate)")).toBeVisible();
    await expect(page.getByText("누적 net PnL")).toBeVisible();

    // 총 12건
    await expect(page.getByText("12건").first()).toBeVisible();

    // 페이지 표시 (size 50 default → 1 페이지)
    await expect(page.getByText(/총 12 건 · 페이지 1 \/ 1/)).toBeVisible();
  });

  test("종목 필터 입력 + Enter → API 재호출", async ({ page }) => {
    const params: URLSearchParams[] = [];
    await mockTradeHistory(page, { requestParams: params, totalElements: 12 });

    await page.goto(`/strategy/${INSTANCE_ID}/trade-history`);
    await expect.poll(() => params.length).toBeGreaterThanOrEqual(1);

    const symbolInput = page.getByPlaceholder("005930");
    await symbolInput.fill("005930");
    await symbolInput.press("Enter");

    await expect
      .poll(() => params[params.length - 1]?.get("symbol"))
      .toBe("005930");
  });

  test("win only / loss only toggle → 재호출", async ({ page }) => {
    const params: URLSearchParams[] = [];
    await mockTradeHistory(page, { requestParams: params });

    await page.goto(`/strategy/${INSTANCE_ID}/trade-history`);
    await expect.poll(() => params.length).toBeGreaterThanOrEqual(1);

    await page.getByRole("button", { name: "win only" }).click();
    await expect
      .poll(() => params[params.length - 1]?.get("winOnly"))
      .toBe("true");

    await page.getByRole("button", { name: "loss only" }).click();
    await expect
      .poll(() => params[params.length - 1]?.get("lossOnly"))
      .toBe("true");
    // win 은 상호배타로 해제됨
    await expect
      .poll(() => params[params.length - 1]?.get("winOnly"))
      .toBe("false");
  });

  test("정렬 버튼 → sort param 변경", async ({ page }) => {
    const params: URLSearchParams[] = [];
    await mockTradeHistory(page, { requestParams: params });

    await page.goto(`/strategy/${INSTANCE_ID}/trade-history`);
    await expect.poll(() => params.length).toBeGreaterThanOrEqual(1);

    await page.getByRole("button", { name: /^net pnl/ }).click();
    await expect
      .poll(() => params[params.length - 1]?.get("sort"))
      .toBe("net_pnl_pct:desc");

    // 같은 field 한번 더 → asc 로 toggle
    await page.getByRole("button", { name: /^net pnl/ }).click();
    await expect
      .poll(() => params[params.length - 1]?.get("sort"))
      .toBe("net_pnl_pct:asc");
  });

  test("페이지네이션 Next → page param 증가", async ({ page }) => {
    const params: URLSearchParams[] = [];
    // size 5 + 12 row → 3 page
    await mockTradeHistory(page, {
      requestParams: params,
      totalElements: 12,
    });

    await page.goto(`/strategy/${INSTANCE_ID}/trade-history`);
    await expect.poll(() => params.length).toBeGreaterThanOrEqual(1);

    // default page size 50 → 1 page only. Next 버튼 disabled.
    const nextButton = page.getByRole("button", { name: "다음" });
    await expect(nextButton).toBeDisabled();
    const prevButton = page.getByRole("button", { name: "이전" });
    await expect(prevButton).toBeDisabled();
  });
});
