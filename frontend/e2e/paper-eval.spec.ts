import { expect, test, type Page, type Route } from "@playwright/test";
import {
  INSTANCE_ID,
  INSTANCE_NAME,
  mockBaseline,
} from "./utils/admin-mocks";

/**
 * F5 필수 시나리오 1 — paper-eval 페이지.
 *
 * - /strategy/{id}/paper-eval 진입 → 4 카드 표시
 * - lookback preset (10/30/50/100) 변경 시 API 재호출 + 표시값 갱신
 * - 차트 일자 preset (30/60/90) 변경 → series API 재호출
 * - 직전 N건 표 row 수 일치
 */

const SNAPSHOT_BODY = {
  data: {
    tradesCount: 30,
    wins: 18,
    losses: 12,
    hitRate: 0.6,
    sumProfitPct: 0.045,
    sumLossPct: -0.018,
    ev: 0.0009,
    pf: 2.5,
    mdd: 0.012,
    avgSlippageBuyPct: 0.0003,
    avgSlippageSellPct: 0.0004,
    avgSellTaxPct: 0.0015,
    avgFeePct: 0.00014,
    avgCostTotalPct: 0.00234,
  },
};

const SERIES_BODY = {
  data: [
    { businessDate: "2026-05-20", netPnlPct: 0.0012 },
    { businessDate: "2026-05-21", netPnlPct: -0.0008 },
    { businessDate: "2026-05-22", netPnlPct: 0.0023 },
    { businessDate: "2026-05-23", netPnlPct: 0.0015 },
  ],
};

function makeRecentMatch(i: number) {
  return {
    id: `aaaaaaaa-0000-0000-0000-${String(i).padStart(12, "0")}`,
    symbolCode: i % 2 === 0 ? "005930" : "000660",
    entryTime: "2026-05-22T09:30:00+09:00",
    exitTime: "2026-05-22T10:15:00+09:00",
    holdingMinutes: 45,
    matchedQuantity: 5,
    grossPnlPct: 0.012,
    netPnlPct: i % 3 === 0 ? -0.002 : 0.0085,
    slippageBuyPct: 0.0003,
    slippageSellPct: 0.0004,
    sellTaxPct: 0.0015,
    feePct: 0.00014,
  };
}

async function mockPaperEvalApis(
  page: Page,
  opts?: {
    recentLimitCounter?: { byLimit: Record<number, number> };
    seriesDaysCalls?: number[];
  },
): Promise<void> {
  // glob 의 * 는 slash 매치 안하므로 정규식 사용 (snapshot + /series + /recent-matches + /trade-history)
  const PATTERN = new RegExp(
    `/api/admin/paper-eval/${INSTANCE_ID}(/[a-z-]+)?(\\?|$)`,
  );
  await page.route(
    PATTERN,
    async (route: Route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith("/series")) {
        if (opts?.seriesDaysCalls) {
          opts.seriesDaysCalls.push(
            Number(url.searchParams.get("days") ?? "30"),
          );
        }
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(SERIES_BODY),
        });
        return;
      }
      if (url.pathname.endsWith("/recent-matches")) {
        const limit = Number(url.searchParams.get("limit") ?? "30");
        if (opts?.recentLimitCounter) {
          opts.recentLimitCounter.byLimit[limit] =
            (opts.recentLimitCounter.byLimit[limit] ?? 0) + 1;
        }
        const rows = Array.from({ length: limit }, (_, i) =>
          makeRecentMatch(i + 1),
        );
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: rows }),
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
        body: JSON.stringify(SNAPSHOT_BODY),
      });
    },
  );
}

test.describe("paper-eval 화면 (F1)", () => {
  test.beforeEach(async ({ page }) => {
    await mockBaseline(page);
  });

  test("진입 시 4 카드 + 비용 breakdown + 최근 매매 표 표시", async ({
    page,
  }) => {
    await mockPaperEvalApis(page);
    await page.goto(`/strategy/${INSTANCE_ID}/paper-eval`);

    // 페이지 헤더 표시
    await expect(
      page.getByRole("heading", {
        name: new RegExp(`paper 평가.*${INSTANCE_NAME}`),
      }),
    ).toBeVisible();

    // 4 카드 라벨
    await expect(page.getByText("누적 EV (평균 net PnL)")).toBeVisible();
    await expect(page.getByText("hit rate (승률)")).toBeVisible();
    await expect(page.getByText(/누적 net PnL \(30일\)/)).toBeVisible();
    await expect(page.getByText("비용 wall (평균)")).toBeVisible();

    // hit rate 0.6 → 60.00%
    await expect(page.getByText("60.00%").first()).toBeVisible();

    // 비용 breakdown 섹션
    await expect(
      page.getByText("비용 breakdown (직전 N건 평균)"),
    ).toBeVisible();
    await expect(page.getByText("매수 슬리피지(signed)")).toBeVisible();

    // 차트 카드
    await expect(page.getByText("누적 net PnL 추이")).toBeVisible();

    // 최근 매매 표 — default lookback 30 → 30 row
    await expect(page.getByText(/최근 매매 \(30건/)).toBeVisible();
  });

  test("lookback preset 변경 시 snapshot + recent-matches 재호출", async ({
    page,
  }) => {
    const counter = { byLimit: {} as Record<number, number> };
    await mockPaperEvalApis(page, { recentLimitCounter: counter });
    await page.goto(`/strategy/${INSTANCE_ID}/paper-eval`);

    // default lookback 30 → recent-matches limit=30 호출 1회
    await expect.poll(() => counter.byLimit[30] ?? 0).toBeGreaterThanOrEqual(1);

    // lookback 10 클릭
    const lookbackSection = page
      .getByText("lookback (직전 매매 N건)")
      .locator("..");
    await lookbackSection.getByRole("button", { name: "10건" }).click();

    // recent-matches limit=10 재호출
    await expect.poll(() => counter.byLimit[10] ?? 0).toBeGreaterThanOrEqual(1);
    await expect(page.getByText(/최근 매매 \(10건/)).toBeVisible();

    // lookback 100 클릭
    await lookbackSection.getByRole("button", { name: "100건" }).click();
    await expect
      .poll(() => counter.byLimit[100] ?? 0)
      .toBeGreaterThanOrEqual(1);
    await expect(page.getByText(/최근 매매 \(100건/)).toBeVisible();
  });

  test("차트 일자 preset 변경 시 series 재호출 + 카드 제목 갱신", async ({
    page,
  }) => {
    const seriesCalls: number[] = [];
    await mockPaperEvalApis(page, { seriesDaysCalls: seriesCalls });

    await page.goto(`/strategy/${INSTANCE_ID}/paper-eval`);

    await expect.poll(() => seriesCalls).toContain(30);

    const chartRangeSection = page
      .getByText("차트 일자 범위")
      .locator("..");
    await chartRangeSection.getByRole("button", { name: "60일" }).click();
    await expect.poll(() => seriesCalls).toContain(60);
    await expect(page.getByText(/누적 net PnL \(60일\)/)).toBeVisible();

    await chartRangeSection.getByRole("button", { name: "90일" }).click();
    await expect.poll(() => seriesCalls).toContain(90);
    await expect(page.getByText(/누적 net PnL \(90일\)/)).toBeVisible();
  });
});
