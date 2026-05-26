import { useEffect, useMemo, useRef } from "react";
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  type IChartApi,
  type ISeriesApi,
  type ISeriesMarkersPluginApi,
  type LogicalRange,
  type SeriesMarker,
  type Time,
  type UTCTimestamp,
  createChart,
  createSeriesMarkers,
} from "lightweight-charts";
import type { CandlestickData, HistogramData } from "lightweight-charts";
import type { ChartOrderOverlay, MinuteBar } from "@/lib/api-types";
import { cn } from "@/lib/utils";

const MIN_HISTORY_LOAD_THRESHOLD = 120;

interface MinuteChartProps {
  bars: MinuteBar[];
  overlays: ChartOrderOverlay[];
  className?: string;
  canLoadOlderHistory?: boolean;
  isLoadingMoreHistory?: boolean;
  onRequestOlderHistory?: () => void;
}

export default function MinuteChart({
  bars,
  overlays,
  className,
  canLoadOlderHistory = true,
  isLoadingMoreHistory = false,
  onRequestOlderHistory,
}: MinuteChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef =
    useRef<ISeriesApi<"Candlestick", Time> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram", Time> | null>(null);
  const markerPluginRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null);
  const hasInitialViewRef = useRef(false);
  const loadRequestBlockedRef = useRef(false);
  const previousVisibleRangeRef = useRef<LogicalRange | null>(null);
  const previousFirstBarTimeRef = useRef<string | null>(null);
  const previousBarCountRef = useRef(0);
  const lastBarIndexRef = useRef(-1);

  const candleData = useMemo(() => bars.map(toCandlestickData), [bars]);
  const volumeData = useMemo(() => bars.map(toVolumeData), [bars]);
  const markerData = useMemo(
    () => buildOrderMarkers(bars, overlays),
    [bars, overlays],
  );

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const palette = resolveChartPalette();
    const chart = createChart(containerRef.current, {
      autoSize: true,
      height: 420,
      layout: {
        background: {
          type: ColorType.Solid,
          color: "transparent",
        },
        textColor: palette.mutedForeground,
        attributionLogo: true,
      },
      localization: {
        locale: "ko-KR",
        priceFormatter: (price: number) =>
          new Intl.NumberFormat("ko-KR", {
            maximumFractionDigits: 0,
          }).format(price),
      },
      rightPriceScale: {
        borderColor: palette.border,
        scaleMargins: {
          top: 0.1,
          bottom: 0.25,
        },
      },
      timeScale: {
        borderColor: palette.border,
        barSpacing: 9,
        minBarSpacing: 2.5,
        rightOffset: 0,
        fixRightEdge: true,
        tickMarkFormatter: formatTickMark,
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: {
        vertLine: {
          color: palette.border,
          labelBackgroundColor: palette.foreground,
        },
        horzLine: {
          color: palette.border,
          labelBackgroundColor: palette.foreground,
        },
      },
      grid: {
        vertLines: {
          color: withAlpha(palette.border, 0.45),
        },
        horzLines: {
          color: withAlpha(palette.border, 0.45),
        },
      },
      handleScroll: {
        mouseWheel: true,
        pressedMouseMove: true,
        horzTouchDrag: true,
        vertTouchDrag: false,
      },
      handleScale: {
        mouseWheel: true,
        pinch: true,
        axisPressedMouseMove: {
          time: true,
          price: false,
        },
        axisDoubleClickReset: {
          time: true,
          price: true,
        },
      },
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: palette.loss,
      downColor: palette.profit,
      borderVisible: false,
      wickUpColor: palette.loss,
      wickDownColor: palette.profit,
      lastValueVisible: true,
      priceLineVisible: true,
    });

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceScaleId: "volume",
      priceLineVisible: false,
      lastValueVisible: false,
      base: 0,
    });

    chart.priceScale("volume").applyOptions({
      scaleMargins: {
        top: 0.78,
        bottom: 0,
      },
    });

    const markerPlugin = createSeriesMarkers(candleSeries, [], {
      autoScale: false,
      zOrder: "top",
    });

    const handleVisibleRangeChange = (range: LogicalRange | null) => {
      if (range === null) {
        previousVisibleRangeRef.current = null;
        return;
      }

      const clampedRange = clampFutureRange(range, lastBarIndexRef.current);
      if (clampedRange !== null) {
        previousVisibleRangeRef.current = clampedRange;
        chart.timeScale().setVisibleLogicalRange(clampedRange);
        return;
      }

      const barsInfo = candleSeries.barsInLogicalRange(range);
      const clampedPastRange = clampPastRange(range, barsInfo, canLoadOlderHistory);
      if (clampedPastRange !== null) {
        previousVisibleRangeRef.current = clampedPastRange;
        chart.timeScale().setVisibleLogicalRange(clampedPastRange);
        return;
      }

      if (isSafeRestoreRange(barsInfo)) {
        previousVisibleRangeRef.current = range;
      }
      if (
        !onRequestOlderHistory ||
        isLoadingMoreHistory ||
        loadRequestBlockedRef.current
      ) {
        return;
      }

      const loadThreshold = calculateHistoryLoadThreshold(range);
      if (barsInfo && barsInfo.barsBefore < loadThreshold) {
        loadRequestBlockedRef.current = true;
        onRequestOlderHistory();
      }
    };

    chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleRangeChange);

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;
    markerPluginRef.current = markerPlugin;

    return () => {
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(
        handleVisibleRangeChange,
      );
      markerPluginRef.current = null;
      candleSeriesRef.current = null;
      volumeSeriesRef.current = null;
      chartRef.current = null;
      chart.remove();
    };
  }, [canLoadOlderHistory, isLoadingMoreHistory, onRequestOlderHistory]);

  useEffect(() => {
    if (
      !chartRef.current ||
      !candleSeriesRef.current ||
      !volumeSeriesRef.current ||
      !markerPluginRef.current
    ) {
      return;
    }

    candleSeriesRef.current.setData(candleData);
    volumeSeriesRef.current.setData(volumeData);
    markerPluginRef.current.setMarkers(markerData);
    lastBarIndexRef.current = bars.length - 1;

    const previousFirstBarTime = previousFirstBarTimeRef.current;
    const previousBarCount = previousBarCountRef.current;
    const firstBarTime = bars[0]?.barTime ?? null;
    const prependedBarCount = countPrependedBars(bars, previousFirstBarTime);

    if (!hasInitialViewRef.current) {
      chartRef.current.timeScale().fitContent();
      hasInitialViewRef.current = true;
    } else if (
      prependedBarCount > 0 &&
      previousVisibleRangeRef.current !== null
    ) {
      chartRef.current.timeScale().setVisibleLogicalRange({
        from: previousVisibleRangeRef.current.from + prependedBarCount,
        to: previousVisibleRangeRef.current.to + prependedBarCount,
      });
    }

    if (bars.length > previousBarCount || firstBarTime !== previousFirstBarTime) {
      loadRequestBlockedRef.current = false;
    }

    previousFirstBarTimeRef.current = firstBarTime;
    previousBarCountRef.current = bars.length;
  }, [bars, candleData, markerData, volumeData]);

  useEffect(() => {
    if (
      canLoadOlderHistory ||
      isLoadingMoreHistory ||
      !chartRef.current ||
      !candleSeriesRef.current
    ) {
      return;
    }

    const range = chartRef.current.timeScale().getVisibleLogicalRange();
    if (range === null) {
      return;
    }
    const barsInfo = candleSeriesRef.current.barsInLogicalRange(range);
    const clampedRange = clampPastRange(range, barsInfo, false);
    if (clampedRange !== null) {
      previousVisibleRangeRef.current = clampedRange;
      chartRef.current.timeScale().setVisibleLogicalRange(clampedRange);
    }
  }, [canLoadOlderHistory, isLoadingMoreHistory, bars]);

  useEffect(() => {
    if (!isLoadingMoreHistory) {
      loadRequestBlockedRef.current = false;
    }
  }, [isLoadingMoreHistory]);

  return (
    <div
      className={cn(
        "rounded-xl border border-border/70 bg-muted/20 p-2 sm:p-3",
        className,
      )}
    >
      <div className="relative">
        <div
          ref={containerRef}
          className="h-[420px] w-full overflow-hidden rounded-lg bg-card"
        />
        <div className="pointer-events-none absolute top-3 left-3 flex flex-wrap gap-2">
          <span className="rounded-full bg-card/90 px-2 py-1 text-[11px] text-muted-foreground shadow-sm ring-1 ring-border/60 backdrop-blur">
            휠로 확대/축소, 드래그로 이동
          </span>
          {isLoadingMoreHistory && (
            <span className="rounded-full bg-card/90 px-2 py-1 text-[11px] text-muted-foreground shadow-sm ring-1 ring-border/60 backdrop-blur">
              과거 데이터 확장 중
            </span>
          )}
        </div>
      </div>
      <p className="mt-3 text-xs text-muted-foreground">
        매수 마커는 위 화살표, 매도 마커는 아래 화살표로 표시됩니다. 차트 왼쪽
        끝으로 이동하면 과거 구간을 자동으로 더 불러옵니다.
      </p>
    </div>
  );
}

function toCandlestickData(bar: MinuteBar): CandlestickData<UTCTimestamp> {
  return {
    time: toUtcTimestamp(bar.barTime),
    open: bar.openPrice,
    high: bar.highPrice,
    low: bar.lowPrice,
    close: bar.closePrice,
  };
}

function toVolumeData(bar: MinuteBar): HistogramData<UTCTimestamp> {
  const isUp = bar.closePrice >= bar.openPrice;
  return {
    time: toUtcTimestamp(bar.barTime),
    value: bar.volume,
    color: isUp ? "rgba(52, 152, 219, 0.35)" : "rgba(231, 76, 60, 0.35)",
  };
}

function buildOrderMarkers(
  bars: MinuteBar[],
  overlays: ChartOrderOverlay[],
): SeriesMarker<UTCTimestamp>[] {
  if (bars.length === 0 || overlays.length === 0) {
    return [];
  }

  const barTimes = bars.map((bar) => toUtcTimestamp(bar.barTime));
  return overlays
    .map((overlay) => toOrderMarker(barTimes, overlay))
    .filter((marker): marker is SeriesMarker<UTCTimestamp> => marker !== null)
    .sort((left, right) => left.time - right.time);
}

function toOrderMarker(
  barTimes: UTCTimestamp[],
  overlay: ChartOrderOverlay,
): SeriesMarker<UTCTimestamp> | null {
  const eventTime = toUtcTimestamp(overlay.filledAt ?? overlay.requestedAt);
  const snappedTime = findBarTimeAtOrBefore(barTimes, eventTime);
  if (snappedTime === null) {
    return null;
  }

  const isBuy = overlay.side === "BUY";
  return {
    id: overlay.tradeOrderId,
    time: snappedTime,
    position: isBuy ? "belowBar" : "aboveBar",
    shape: isBuy ? "arrowUp" : "arrowDown",
    color: isBuy ? "#10b981" : "#f59e0b",
  };
}

function toUtcTimestamp(isoValue: string): UTCTimestamp {
  return Math.floor(new Date(isoValue).getTime() / 1000) as UTCTimestamp;
}

function findBarTimeAtOrBefore(
  barTimes: UTCTimestamp[],
  targetTime: UTCTimestamp,
): UTCTimestamp | null {
  let low = 0;
  let high = barTimes.length - 1;
  let candidate: UTCTimestamp | null = null;

  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    const time = barTimes[mid];
    if (time <= targetTime) {
      candidate = time;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  return candidate;
}

function clampFutureRange(range: LogicalRange, lastIndex: number) {
  if (lastIndex < 0 || range.to <= lastIndex) {
    return null;
  }

  const width = range.to - range.from;
  return {
    from: (lastIndex - width) as LogicalRange["from"],
    to: lastIndex as LogicalRange["to"],
  };
}

function clampPastRange(
  range: LogicalRange,
  barsInfo: ReturnType<ISeriesApi<"Candlestick", Time>["barsInLogicalRange"]>,
  canLoadOlderHistory: boolean,
) {
  if (canLoadOlderHistory || range.from >= 0) {
    return null;
  }
  if (barsInfo === null || barsInfo.barsBefore >= 0) {
    return null;
  }

  const width = range.to - range.from;
  return {
    from: 0 as LogicalRange["from"],
    to: width as LogicalRange["to"],
  };
}

function calculateHistoryLoadThreshold(range: LogicalRange) {
  const visibleBars = Math.max(0, Math.ceil(range.to - range.from));
  return Math.max(MIN_HISTORY_LOAD_THRESHOLD, visibleBars);
}

function isSafeRestoreRange(
  barsInfo: ReturnType<ISeriesApi<"Candlestick", Time>["barsInLogicalRange"]>,
) {
  return barsInfo !== null && barsInfo.barsBefore >= 0;
}

function formatTickMark(time: Time) {
  const date = toDate(time);
  if (!date) {
    return null;
  }

  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });

  const parts = formatter.formatToParts(date);
  const month = parts.find((part) => part.type === "month")?.value ?? "";
  const day = parts.find((part) => part.type === "day")?.value ?? "";
  const hour = parts.find((part) => part.type === "hour")?.value ?? "";
  const minute = parts.find((part) => part.type === "minute")?.value ?? "";

  return `${month}.${day} ${hour}:${minute}`;
}

function toDate(time: Time): Date | null {
  if (typeof time === "number") {
    return new Date(time * 1000);
  }
  if (typeof time === "string") {
    return new Date(time);
  }
  if (
    typeof time === "object" &&
    typeof time.year === "number" &&
    typeof time.month === "number" &&
    typeof time.day === "number"
  ) {
    return new Date(Date.UTC(time.year, time.month - 1, time.day));
  }
  return null;
}

function countPrependedBars(
  bars: MinuteBar[],
  previousFirstBarTime: string | null,
) {
  if (!previousFirstBarTime || bars.length === 0) {
    return 0;
  }
  const previousStartIndex = bars.findIndex(
    (bar) => bar.barTime === previousFirstBarTime,
  );
  return previousStartIndex > 0 ? previousStartIndex : 0;
}

function resolveChartPalette() {
  return {
    border: resolveCssColor("--border", "#d9d9d9"),
    foreground: resolveCssColor("--foreground", "#111827"),
    mutedForeground: resolveCssColor("--muted-foreground", "#6b7280"),
    profit: resolveCssColor("--profit", "#e74c3c"),
    loss: resolveCssColor("--loss", "#3498db"),
  };
}

function resolveCssColor(
  token: string,
  fallback: string,
) {
  const rootStyles = getComputedStyle(document.documentElement);
  const rawToken = rootStyles.getPropertyValue(token).trim();
  if (!rawToken) {
    return fallback;
  }

  const resolved = normalizeCssColor(rawToken);
  return resolved ?? fallback;
}

function withAlpha(rgbColor: string, alpha: number) {
  const match = rgbColor.match(/\d+(\.\d+)?/g);
  if (!match || match.length < 3) {
    return rgbColor;
  }
  const [r, g, b] = match;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function normalizeCssColor(input: string) {
  const canvas = document.createElement("canvas");
  canvas.width = 1;
  canvas.height = 1;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) {
    return null;
  }

  context.clearRect(0, 0, 1, 1);
  context.fillStyle = "#000000";
  context.fillStyle = input;
  context.fillRect(0, 0, 1, 1);
  const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
  const alpha = Number((a / 255).toFixed(4));
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
