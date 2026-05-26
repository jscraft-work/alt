import { readFileSync } from "node:fs";
import { expect, test, type Page } from "@playwright/test";

type ChartScenario = {
  symbolCode?: string;
  symbolName?: string;
  overlays?: boolean;
  extraHistoryDaysAvailable?: number;
};

type MinuteRequestLog = {
  dateFrom: string;
  dateTo: string;
  returnedBars: number;
  firstReturnedDay: string | null;
};

type MinuteBarFixture = {
  barTime: string;
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  volume: number;
};

type OverlayFixture = {
  tradeOrderId: string;
  side: string;
  orderStatus: string;
  requestedAt: string;
  filledAt: string | null;
  requestedPrice: number | null;
  avgFilledPrice: number | null;
  requestedQuantity: number;
};

const DEFAULT_SYMBOL_CODE = "000270";
const DEFAULT_SYMBOL_NAME = "기아";
const DEFAULT_INSTANCE_ID = "fd5d5c5f-a67a-420c-bf44-f2b59da7c411";
const INITIAL_VISIBLE_PIXEL_THRESHOLD = 40;
const INITIAL_SERIES_PIXEL_THRESHOLD = 24;
const REAL_KIA_MAY_FIXTURE = JSON.parse(
  readFileSync(new URL("./fixtures/kia-may-2026.json", import.meta.url), "utf-8"),
) as {
  bars: MinuteBarFixture[];
  overlays: OverlayFixture[];
};
const GAPPED_KAKAO_FIXTURE = {
  bars: buildFixtureBarsForDays("035720", [
    "2026-05-15",
    "2026-05-18",
    "2026-05-19",
    "2026-05-20",
    "2026-05-21",
    "2026-05-22",
    "2026-05-26",
  ]),
  overlays: [] as OverlayFixture[],
};

test.describe("chart page e2e", () => {
  test("renders chart with overlays and keeps canvases alive after right drag", async ({
    page,
  }) => {
    const pageErrors = capturePageErrors(page);
    await mockChartApis(page, {
      overlays: true,
      extraHistoryDaysAvailable: 5,
    });

    await page.goto("/chart");
    await waitForChartReady(page);
    await dragChart(page, 600);
    await expectChartRendered(page);
    expect(pageErrors).toEqual([]);
    await expect(page.getByText("주문 2건")).toBeVisible();
  });

  test("uses older-history backfill path and keeps chart rendered", async ({
    page,
  }) => {
    const pageErrors = capturePageErrors(page);
    const minuteRequests: MinuteRequestLog[] = [];
    await mockChartApis(
      page,
      {
        overlays: true,
        extraHistoryDaysAvailable: 5,
      },
      minuteRequests,
    );

    await page.goto("/chart");
    await waitForChartReady(page);

    await dragLeftUntil(
      page,
      () => uniqueDateFromCount(minuteRequests) >= 2,
    );

    await waitForHistoryLoadingToFinish(page);
    await expectChartRendered(page);
    expect(uniqueDateFromCount(minuteRequests)).toBeGreaterThanOrEqual(2);
    expect(pageErrors).toEqual([]);
  });

  test("stays intact when older-history requests return no new bars", async ({
    page,
  }) => {
    const pageErrors = capturePageErrors(page);
    const minuteRequests: MinuteRequestLog[] = [];
    await mockChartApis(
      page,
      {
        overlays: true,
        extraHistoryDaysAvailable: 0,
      },
      minuteRequests,
    );

    await page.goto("/chart");
    await waitForChartReady(page);
    const beforeCount = uniqueDateFromCount(minuteRequests);

    await dragLeftUntil(
      page,
      () => uniqueDateFromCount(minuteRequests) > beforeCount,
      8,
    );

    await waitForHistoryLoadingToFinish(page);
    await dragChart(page, -500);
    await expectChartRendered(page);
    expect(pageErrors).toEqual([]);
  });

  test("does not spin forever on no-more-history responses", async ({ page }) => {
    const minuteRequests: MinuteRequestLog[] = [];
    await mockChartApis(
      page,
      {
        overlays: false,
        extraHistoryDaysAvailable: 0,
      },
      minuteRequests,
    );

    await page.goto("/chart");
    await waitForChartReady(page);
    const settledCount = minuteRequests.length;

    await dragChart(page, -900);
    await waitForHistoryLoadingToFinish(page);
    const afterFirstDragCount = minuteRequests.length;

    await page.waitForTimeout(600);
    await dragChart(page, -900);
    await waitForHistoryLoadingToFinish(page);
    await page.waitForTimeout(600);

    expect(afterFirstDragCount).toBeGreaterThanOrEqual(settledCount);
    expect(minuteRequests.length - afterFirstDragCount).toBeLessThanOrEqual(1);
    await expectChartRendered(page);
  });

  test("renders normally when overlays are empty", async ({ page }) => {
    const pageErrors = capturePageErrors(page);
    await mockChartApis(page, {
      overlays: false,
      extraHistoryDaysAvailable: 3,
    });

    await page.goto("/chart");
    await waitForChartReady(page);
    await dragChart(page, -400);
    await expectChartRendered(page);
    expect(pageErrors).toEqual([]);
    await expect(page.getByText("주문 0건")).toBeVisible();
  });

  test("keeps chart alive when panning real kia may data back to early may", async ({
    page,
  }) => {
    const pageErrors = capturePageErrors(page);
    const minuteRequests: MinuteRequestLog[] = [];
    await mockRealKiaMayApis(page, minuteRequests);

    await page.goto("/chart");
    await setAnchorDate(page, "2026-05-26");
    await waitForChartReady(page);

    await dragLeftUntil(
      page,
      () => {
        const oldestRequest = minuteRequests.at(-1);
        return oldestRequest !== undefined && oldestRequest.dateFrom <= "2026-05-04";
      },
      14,
    );

    await waitForHistoryLoadingToFinish(page);
    await dragChart(page, -900);
    await expectChartRendered(page);
    expect(pageErrors).toEqual([]);
  });

  test("keeps extending through empty days until the last available trading day", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    const minuteRequests: MinuteRequestLog[] = [];
    await mockFixtureChartApis(
      page,
      "035720",
      "카카오",
      GAPPED_KAKAO_FIXTURE.bars,
      GAPPED_KAKAO_FIXTURE.overlays,
      minuteRequests,
    );

    await page.goto("/chart");
    await setSymbolCode(page, "035720");
    await setAnchorDate(page, "2026-05-26");
    await waitForChartReady(page);

    await dragLeftUntil(
      page,
      () => minuteRequests.some((request) => request.dateFrom <= "2026-05-15"),
      20,
    );

    await waitForHistoryLoadingToFinish(page);
    await expectChartRendered(page);
    expect(
      minuteRequests.some((request) => request.dateFrom <= "2026-05-15"),
    ).toBe(true);
  });
});

function capturePageErrors(page: Page) {
  const errors: string[] = [];
  page.on("pageerror", (error) => {
    errors.push(error.message);
  });
  return errors;
}

async function mockChartApis(
  page: Page,
  scenario: ChartScenario,
  minuteRequests: MinuteRequestLog[] = [],
) {
  const symbolCode = scenario.symbolCode ?? DEFAULT_SYMBOL_CODE;
  const symbolName = scenario.symbolName ?? DEFAULT_SYMBOL_NAME;
  let initialDateFrom: string | null = null;

  await page.route("**/api/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        "Set-Cookie": "XSRF-TOKEN=test-token; Path=/",
      },
      contentType: "application/json",
      body: JSON.stringify({ data: { ok: true } }),
    });
  });

  await page.route("**/api/auth/me", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({
        error: {
          code: "UNAUTHORIZED",
          message: "unauthorized",
        },
      }),
    });
  });

  await page.route("**/api/admin/assets**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [
          {
            id: "asset-1",
            symbolCode,
            symbolName,
            marketType: "KOSPI",
            dartCorpCode: null,
            hidden: false,
            version: 1,
            updatedAt: "2026-05-26T09:00:00+09:00",
          },
        ],
      }),
    });
  });

  await page.route("**/api/dashboard/strategy-overview", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [
          {
            strategyInstanceId: DEFAULT_INSTANCE_ID,
            name: `박스 단타 v1 - ${symbolName} (${symbolCode})`,
            executionMode: "paper",
            lifecycleState: "active",
            autoPausedReason: null,
            budgetAmount: 1000000,
            cashAmount: 500000,
            totalAssetAmount: 1000000,
            todayRealizedPnl: 0,
            latestDecisionStatus: "HOLD",
            latestDecisionAt: "2026-05-26T15:20:00+09:00",
            watchlistCount: 1,
          },
        ],
      }),
    });
  });

  await page.route("**/api/charts/order-overlays**", async (route) => {
    const url = new URL(route.request().url());
    const dateFrom = url.searchParams.get("dateFrom") ?? "2026-05-20";
    const overlays = scenario.overlays
      ? [
          {
            tradeOrderId: "order-1",
            side: "BUY",
            orderStatus: "filled",
            requestedAt: `${dateFrom}T10:15:00+09:00`,
            filledAt: `${dateFrom}T10:15:00+09:00`,
            requestedPrice: 165000,
            avgFilledPrice: 165000,
            requestedQuantity: 5,
          },
          {
            tradeOrderId: "order-2",
            side: "SELL",
            orderStatus: "filled",
            requestedAt: `${shiftDate(dateFrom, 1)}T14:25:00+09:00`,
            filledAt: `${shiftDate(dateFrom, 1)}T14:25:00+09:00`,
            requestedPrice: 169000,
            avgFilledPrice: 169000,
            requestedQuantity: 5,
          },
        ]
      : [];
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: overlays }),
    });
  });

  await page.route("**/api/charts/minutes**", async (route) => {
    const url = new URL(route.request().url());
    const dateFrom = url.searchParams.get("dateFrom") ?? "2026-05-20";
    const dateTo = url.searchParams.get("dateTo") ?? "2026-05-26";

    if (initialDateFrom === null) {
      initialDateFrom = dateFrom;
    }

    const earliestAvailableDate = shiftDate(
      initialDateFrom,
      -(scenario.extraHistoryDaysAvailable ?? 0),
    );
    const effectiveDateFrom =
      compareDates(dateFrom, earliestAvailableDate) < 0
        ? earliestAvailableDate
        : dateFrom;
    const bars = buildMinuteBars(symbolCode, effectiveDateFrom, dateTo);
    minuteRequests.push({
      dateFrom,
      dateTo,
      returnedBars: bars.length,
      firstReturnedDay: bars[0]?.barTime.slice(0, 10) ?? null,
    });

    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          symbolCode,
          dateFrom,
          dateTo,
          bars,
        },
      }),
    });
  });
}

async function mockRealKiaMayApis(
  page: Page,
  minuteRequests: MinuteRequestLog[] = [],
) {
  await mockFixtureChartApis(
    page,
    DEFAULT_SYMBOL_CODE,
    DEFAULT_SYMBOL_NAME,
    REAL_KIA_MAY_FIXTURE.bars,
    REAL_KIA_MAY_FIXTURE.overlays,
    minuteRequests,
  );
}

async function mockFixtureChartApis(
  page: Page,
  symbolCode: string,
  symbolName: string,
  barsFixture: MinuteBarFixture[],
  overlaysFixture: OverlayFixture[],
  minuteRequests: MinuteRequestLog[] = [],
) {
  await mockBaseChartApis(page, symbolCode, symbolName);

  await page.route("**/api/charts/order-overlays**", async (route) => {
    const url = new URL(route.request().url());
    const dateFrom = url.searchParams.get("dateFrom") ?? "2026-05-01";
    const dateTo = url.searchParams.get("dateTo") ?? "2026-05-31";
    const overlays = overlaysFixture.filter((overlay) => {
      const at = overlay.filledAt ?? overlay.requestedAt;
      const day = at.slice(0, 10);
      return day >= dateFrom && day <= dateTo;
    });
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: overlays }),
    });
  });

  await page.route("**/api/charts/minutes**", async (route) => {
    const url = new URL(route.request().url());
    const dateFrom = url.searchParams.get("dateFrom") ?? "2026-05-20";
    const dateTo = url.searchParams.get("dateTo") ?? "2026-05-26";
    const bars = barsFixture.filter((bar) => {
      const day = bar.barTime.slice(0, 10);
      return day >= dateFrom && day <= dateTo;
    });
    minuteRequests.push({
      dateFrom,
      dateTo,
      returnedBars: bars.length,
      firstReturnedDay: bars[0]?.barTime.slice(0, 10) ?? null,
    });
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          symbolCode,
          dateFrom,
          dateTo,
          bars,
        },
      }),
    });
  });
}

async function mockBaseChartApis(
  page: Page,
  symbolCode: string,
  symbolName: string,
) {
  await page.route("**/api/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        "Set-Cookie": "XSRF-TOKEN=test-token; Path=/",
      },
      contentType: "application/json",
      body: JSON.stringify({ data: { ok: true } }),
    });
  });

  await page.route("**/api/auth/me", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({
        error: {
          code: "UNAUTHORIZED",
          message: "unauthorized",
        },
      }),
    });
  });

  await page.route("**/api/admin/assets**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [
          {
            id: "asset-1",
            symbolCode,
            symbolName,
            marketType: "KOSPI",
            dartCorpCode: null,
            hidden: false,
            version: 1,
            updatedAt: "2026-05-26T09:00:00+09:00",
          },
        ],
      }),
    });
  });

  await page.route("**/api/dashboard/strategy-overview", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [
          {
            strategyInstanceId: DEFAULT_INSTANCE_ID,
            name: `박스 단타 v1 - ${symbolName} (${symbolCode})`,
            executionMode: "paper",
            lifecycleState: "active",
            autoPausedReason: null,
            budgetAmount: 1000000,
            cashAmount: 500000,
            totalAssetAmount: 1000000,
            todayRealizedPnl: 0,
            latestDecisionStatus: "HOLD",
            latestDecisionAt: "2026-05-26T15:20:00+09:00",
            watchlistCount: 1,
          },
        ],
      }),
    });
  });
}

async function waitForChartReady(page: Page) {
  await expect(page.getByText("분봉 차트")).toBeVisible();
  await expect(page.getByText("누적 거래량")).toBeVisible();
  await waitForHistoryLoadingToFinish(page);
  await expectChartRendered(page);
}

async function setAnchorDate(page: Page, dateText: string) {
  await page.getByLabel("기준일").fill(dateText);
  await page.getByRole("button", { name: "조회" }).click();
}

async function setSymbolCode(page: Page, symbolCode: string) {
  await page.getByLabel("종목 코드").fill(symbolCode);
}

async function waitForHistoryLoadingToFinish(page: Page) {
  const loadingBadge = page.getByText("과거 데이터 확장 중");
  await loadingBadge.waitFor({ state: "hidden" }).catch(() => undefined);
}

async function expectChartRendered(page: Page) {
  await expect
    .poll(
      () => getChartCanvasStats(page),
      {
        message: "expected chart canvases to stay non-blank",
      },
    )
    .toMatchObject({
      canvasCount: expect.any(Number),
      nonTransparentSamples: expect.any(Number),
      seriesColorSamples: expect.any(Number),
    });

  const stats = await getChartCanvasStats(page);
  expect(stats.canvasCount).toBeGreaterThanOrEqual(4);
  expect(stats.nonTransparentSamples).toBeGreaterThan(
    INITIAL_VISIBLE_PIXEL_THRESHOLD,
  );
  expect(stats.seriesColorSamples).toBeGreaterThan(
    INITIAL_SERIES_PIXEL_THRESHOLD,
  );
}

async function getChartCanvasStats(page: Page) {
  return await page.locator("canvas").evaluateAll((nodes) => {
    const matchesSeriesColor = (
      r: number,
      g: number,
      b: number,
      a: number,
      targetR: number,
      targetG: number,
      targetB: number,
    ) => {
      if (a < 32) {
        return false;
      }

      const distance = Math.sqrt(
        (r - targetR) ** 2 + (g - targetG) ** 2 + (b - targetB) ** 2,
      );
      return distance <= 110;
    };

    const canvases = nodes.filter(
      (node): node is HTMLCanvasElement =>
        node instanceof HTMLCanvasElement &&
        node.width > 0 &&
        node.height > 0 &&
        node.clientWidth > 0 &&
        node.clientHeight > 0,
    );

    let nonTransparentSamples = 0;
    let seriesColorSamples = 0;
    for (const canvas of canvases) {
      const context = canvas.getContext("2d", { willReadFrequently: true });
      if (!context) {
        continue;
      }
      const { width, height } = canvas;
      const imageData = context.getImageData(0, 0, width, height).data;
      const isMainPlotCanvas = width >= 400 && height >= 300;
      const stepX = isMainPlotCanvas
        ? 1
        : Math.max(1, Math.floor(width / 48));
      const stepY = isMainPlotCanvas
        ? 1
        : Math.max(1, Math.floor(height / 24));
      for (let y = 0; y < height; y += stepY) {
        for (let x = 0; x < width; x += stepX) {
          const index = (y * width + x) * 4;
          const r = imageData[index];
          const g = imageData[index + 1];
          const b = imageData[index + 2];
          const a = imageData[index + 3];
          if (a > 0) {
            nonTransparentSamples++;
          }
          if (
            matchesSeriesColor(r, g, b, a, 52, 152, 219) ||
            matchesSeriesColor(r, g, b, a, 231, 76, 60)
          ) {
            seriesColorSamples++;
          }
        }
      }
    }

    return {
      canvasCount: canvases.length,
      nonTransparentSamples,
      seriesColorSamples,
    };
  });
}

async function dragChart(page: Page, deltaX: number) {
  const surface = page.getByTestId("minute-chart-surface");
  const box = await surface.boundingBox();
  if (!box) {
    throw new Error("chart surface bounding box unavailable");
  }

  const startX = box.x + box.width * 0.55;
  const startY = box.y + box.height * 0.45;
  await page.mouse.move(startX, startY);
  await page.mouse.down();
  await page.mouse.move(startX + deltaX, startY, { steps: 20 });
  await page.mouse.up();
}

async function dragLeftUntil(
  page: Page,
  condition: () => boolean,
  maxAttempts = 6,
) {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (condition()) {
      return;
    }
    await dragChart(page, 900);
    await waitForHistoryLoadingToFinish(page);
    await page.waitForTimeout(250);
  }
}

function buildMinuteBars(
  symbolCode: string,
  dateFrom: string,
  dateTo: string,
) {
  const bars: Array<{
    barTime: string;
    openPrice: number;
    highPrice: number;
    lowPrice: number;
    closePrice: number;
    volume: number;
  }> = [];

  const start = parseDate(dateFrom);
  const end = parseDate(dateTo);
  let cursor = new Date(start.getTime());
  let dayIndex = 0;

  while (cursor <= end) {
    const dayOfWeek = cursor.getUTCDay();
    if (dayOfWeek !== 0 && dayOfWeek !== 6) {
      const dateText = formatDate(cursor);
      let price = 160000 + dayIndex * 1200;
      for (let i = 0; i < 27; i++) {
        const minute = i * 15;
        const hour = 9 + Math.floor(minute / 60);
        const minuteOfHour = minute % 60;
        const open = price;
        const close = price + (i % 2 === 0 ? 120 : -80);
        const high = Math.max(open, close) + 70;
        const low = Math.min(open, close) - 70;
        bars.push({
          barTime: `${dateText}T${String(hour).padStart(2, "0")}:${String(
            minuteOfHour,
          ).padStart(2, "0")}:00+09:00`,
          openPrice: open,
          highPrice: high,
          lowPrice: low,
          closePrice: close,
          volume: 1000 + i * 25 + dayIndex * 100,
        });
        price = close;
      }
      dayIndex++;
    }
    cursor = addDays(cursor, 1);
  }

  if (bars.length === 0) {
    return [
      {
        barTime: `${dateTo}T09:00:00+09:00`,
        openPrice: 160000,
        highPrice: 160150,
        lowPrice: 159900,
        closePrice: 160050,
        volume: 1000,
      },
    ];
  }

  return bars;
}

function buildFixtureBarsForDays(
  symbolCode: string,
  days: string[],
) {
  const allBars: MinuteBarFixture[] = [];
  let dayIndex = 0;

  for (const day of days) {
    allBars.push(...buildMinuteBars(symbolCode, day, day).map((bar) => ({
      ...bar,
      openPrice: bar.openPrice + dayIndex * 300,
      highPrice: bar.highPrice + dayIndex * 300,
      lowPrice: bar.lowPrice + dayIndex * 300,
      closePrice: bar.closePrice + dayIndex * 300,
    })));
    dayIndex++;
  }

  return allBars;
}

function uniqueDateFromCount(requests: MinuteRequestLog[]) {
  return new Set(requests.map((request) => request.dateFrom)).size;
}

function shiftDate(dateText: string, days: number) {
  const date = parseDate(dateText);
  return formatDate(addDays(date, days));
}

function compareDates(left: string, right: string) {
  return parseDate(left).getTime() - parseDate(right).getTime();
}

function parseDate(dateText: string) {
  return new Date(`${dateText}T00:00:00Z`);
}

function addDays(date: Date, days: number) {
  const next = new Date(date.getTime());
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

function formatDate(date: Date) {
  return date.toISOString().slice(0, 10);
}
