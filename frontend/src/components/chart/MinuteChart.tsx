import { useEffect, useMemo, useRef } from "react";
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  type IChartApi,
  type LineData,
  type ISeriesApi,
  type ISeriesMarkersPluginApi,
  type LogicalRange,
  type SeriesMarker,
  type Time,
  type UTCTimestamp,
  createChart,
  createSeriesMarkers,
  LineSeries,
} from "lightweight-charts";
import type {
  CandlestickData,
  HistogramData,
} from "lightweight-charts";
import type { ChartOrderOverlay, MinuteBar } from "@/lib/api-types";
import { cn } from "@/lib/utils";

const HISTORY_LOAD_THRESHOLD = 24;

interface MinuteChartProps {
  bars: MinuteBar[];
  overlays: ChartOrderOverlay[];
  focusedOverlayId?: string | null;
  className?: string;
  isLoadingMoreHistory?: boolean;
  onRequestOlderHistory?: () => void;
}

export default function MinuteChart({
  bars,
  overlays,
  focusedOverlayId = null,
  className,
  isLoadingMoreHistory = false,
  onRequestOlderHistory,
}: MinuteChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef =
    useRef<ISeriesApi<"Candlestick", Time> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram", Time> | null>(null);
  const overlaySeriesRefs = useRef<OverlaySeriesBinding[]>([]);
  const hasInitialViewRef = useRef(false);
  const loadRequestBlockedRef = useRef(false);
  const lastDataSignatureRef = useRef<string>("");
  const previousVisibleRangeRef = useRef<LogicalRange | null>(null);
  const previousFirstBarTimeRef = useRef<string | null>(null);
  const previousBarCountRef = useRef(0);

  const candleData = useMemo(
    () => bars.map(toCandlestickData),
    [bars],
  );
  const volumeData = useMemo(
    () => bars.map(toVolumeData),
    [bars],
  );
  const overlaySeriesData = useMemo(
    () => buildOverlaySeriesData(bars, overlays, focusedOverlayId),
    [bars, overlays, focusedOverlayId],
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
        fixLeftEdge: true,
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

    const handleVisibleRangeChange = (range: LogicalRange | null) => {
      previousVisibleRangeRef.current = range;

      if (
        range === null ||
        !onRequestOlderHistory ||
        isLoadingMoreHistory ||
        loadRequestBlockedRef.current
      ) {
        return;
      }

      const barsInfo = candleSeries.barsInLogicalRange(range);
      if (barsInfo && barsInfo.barsBefore < HISTORY_LOAD_THRESHOLD) {
        loadRequestBlockedRef.current = true;
        onRequestOlderHistory();
      }
    };

    chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleRangeChange);

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;
    overlaySeriesRefs.current = [];

    return () => {
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(
        handleVisibleRangeChange,
      );
      overlaySeriesRefs.current = [];
      candleSeriesRef.current = null;
      volumeSeriesRef.current = null;
      chartRef.current = null;
      chart.remove();
    };
  }, [isLoadingMoreHistory, onRequestOlderHistory]);

  useEffect(() => {
    if (
      !chartRef.current ||
      !candleSeriesRef.current ||
      !volumeSeriesRef.current
    ) {
      return;
    }

    candleSeriesRef.current.setData(candleData);
    volumeSeriesRef.current.setData(volumeData);

    const signature = buildDataSignature(bars);
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
    } else if (signature !== lastDataSignatureRef.current) {
      const currentRange = chartRef.current.timeScale().getVisibleLogicalRange();
      if (currentRange !== null) {
        previousVisibleRangeRef.current = currentRange;
      }
    }

    if (bars.length > previousBarCount || firstBarTime !== previousFirstBarTime) {
      loadRequestBlockedRef.current = false;
    }

    lastDataSignatureRef.current = signature;
    previousFirstBarTimeRef.current = firstBarTime;
    previousBarCountRef.current = bars.length;
  }, [bars, candleData, volumeData]);

  useEffect(() => {
    if (!chartRef.current) {
      return;
    }

    for (const binding of overlaySeriesRefs.current) {
      chartRef.current.removeSeries(binding.series);
    }
    overlaySeriesRefs.current = [];

    for (const slot of overlaySeriesData) {
      const series = chartRef.current.addSeries(LineSeries, {
        color: slot.color,
        lineVisible: false,
        lineWidth: 1,
        pointMarkersVisible: false,
        crosshairMarkerVisible: false,
        lastValueVisible: false,
        priceLineVisible: false,
      });
      series.setData(slot.points);
      const markers = createSeriesMarkers(series, slot.markers, {
        zOrder: "top",
      });
      overlaySeriesRefs.current.push({
        series,
        markers,
      });
    }
  }, [overlaySeriesData]);

  useEffect(() => {
    if (!isLoadingMoreHistory) {
      loadRequestBlockedRef.current = false;
    }
  }, [isLoadingMoreHistory]);

  useEffect(() => {
    if (!chartRef.current || !focusedOverlayId || bars.length === 0) {
      return;
    }

    const focusTarget = resolveFocusedOverlayTarget(
      bars,
      overlays,
      focusedOverlayId,
    );
    if (!focusTarget) {
      return;
    }

    const currentRange = chartRef.current.timeScale().getVisibleLogicalRange();
    const windowSize =
      currentRange === null
        ? Math.min(Math.max(bars.length - 1, 30), 180)
        : Math.max(currentRange.to - currentRange.from, 30);
    const targetIndex = focusTarget.barIndex;
    const maxTo = Math.max(bars.length - 1, 0);
    const maxFrom = Math.max(maxTo - windowSize, 0);
    const nextFrom = clamp(targetIndex - windowSize / 2, 0, maxFrom);
    const nextTo = Math.min(nextFrom + windowSize, maxTo);

    chartRef.current.timeScale().setVisibleLogicalRange({
      from: nextFrom,
      to: nextTo,
    });
  }, [bars, overlays, focusedOverlayId]);

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
        매수 마커는 위 화살표, 매도 마커는 아래 화살표로 표시됩니다. 우측 주문
        목록에서 항목을 누르면 해당 시점으로 차트를 이동합니다. 차트 왼쪽 끝으로
        이동하면 과거 구간을 자동으로 더 불러옵니다.
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

function toUtcTimestamp(isoValue: string): UTCTimestamp {
  return Math.floor(new Date(isoValue).getTime() / 1000) as UTCTimestamp;
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

function buildDataSignature(bars: MinuteBar[]) {
  const first = bars[0]?.barTime ?? "";
  const last = bars[bars.length - 1]?.barTime ?? "";
  return `${bars.length}:${first}:${last}`;
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

function buildOverlaySeriesData(
  bars: MinuteBar[],
  overlays: ChartOrderOverlay[],
  focusedOverlayId: string | null,
): OverlaySeriesInput[] {
  if (bars.length === 0 || overlays.length === 0) {
    return [];
  }

  const barLookup = buildBarLookup(bars);
  const grouped = new Map<string, OverlayPlacement[]>();

  for (const overlay of overlays) {
    const placement = resolveOverlayPlacement(barLookup, overlay);
    if (!placement) {
      continue;
    }
    const bucketKey = `${placement.time}:${placement.side}`;
    const bucket = grouped.get(bucketKey);
    if (bucket) {
      bucket.push(placement);
    } else {
      grouped.set(bucketKey, [placement]);
    }
  }

  const slots = new Map<string, OverlaySeriesInput>();
  for (const placements of grouped.values()) {
    placements
      .sort((left, right) => left.eventTime - right.eventTime)
      .forEach((placement, slotIndex) => {
        const slotKey = `${placement.side}:${slotIndex}`;
        const slot = slots.get(slotKey) ?? createOverlaySlot(slotKey, placement.side);
        const price = computeOverlaySlotPrice(placement, slotIndex);
        slot.points.push({
          time: placement.time,
          value: price,
        });
        slot.markers.push(
          toOrderMarker(
            placement,
            price,
            placement.overlay.tradeOrderId === focusedOverlayId,
          ),
        );
        slots.set(slotKey, slot);
      });
  }

  return Array.from(slots.values());
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

function buildBarLookup(bars: MinuteBar[]) {
  const entries = bars.map((bar, index) => {
    const time = toUtcTimestamp(bar.barTime);
    return {
      time,
      index,
      bar,
    };
  });
  return {
    entries,
    times: entries.map((entry) => entry.time),
  };
}

function resolveOverlayPlacement(
  barLookup: ReturnType<typeof buildBarLookup>,
  overlay: ChartOrderOverlay,
) {
  const eventTime = toUtcTimestamp(overlay.filledAt ?? overlay.requestedAt);
  const snappedTime = findBarTimeAtOrBefore(barLookup.times, eventTime);
  if (snappedTime === null) {
    return null;
  }

  const entry = barLookup.entries.find((candidate) => candidate.time === snappedTime);
  if (!entry) {
    return null;
  }

  const requestedPrice = overlay.avgFilledPrice ?? overlay.requestedPrice;
  const side: "BUY" | "SELL" = overlay.side === "BUY" ? "BUY" : "SELL";
  const fallbackPrice =
    side === "BUY" ? entry.bar.lowPrice : entry.bar.highPrice;

  return {
    overlay,
    eventTime,
    side,
    time: snappedTime,
    barIndex: entry.index,
    highPrice: entry.bar.highPrice,
    lowPrice: entry.bar.lowPrice,
    closePrice: entry.bar.closePrice,
    basePrice: requestedPrice ?? fallbackPrice,
  };
}

function resolveFocusedOverlayTarget(
  bars: MinuteBar[],
  overlays: ChartOrderOverlay[],
  focusedOverlayId: string,
) {
  const barLookup = buildBarLookup(bars);
  const overlay = overlays.find(
    (candidate) => candidate.tradeOrderId === focusedOverlayId,
  );
  if (!overlay) {
    return null;
  }
  return resolveOverlayPlacement(barLookup, overlay);
}

function toOrderMarker(
  placement: OverlayPlacement,
  price: number,
  isFocused: boolean,
): SeriesMarker<UTCTimestamp> {
  const isBuy = placement.side === "BUY";

  return {
    id: placement.overlay.tradeOrderId,
    time: placement.time,
    position: "inBar",
    shape: isBuy ? "arrowUp" : "arrowDown",
    color: isFocused
      ? (isBuy ? "#1d4ed8" : "#b91c1c")
      : (isBuy ? "#3498db" : "#e74c3c"),
    size: isFocused ? 2 : 1,
    price,
    text: isFocused ? placement.side : undefined,
  };
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function createOverlaySlot(
  key: string,
  side: "BUY" | "SELL",
): OverlaySeriesInput {
  return {
    key,
    side,
    color: side === "BUY" ? "#3498db" : "#e74c3c",
    points: [],
    markers: [],
  };
}

function computeOverlaySlotPrice(
  placement: OverlayPlacement,
  slotIndex: number,
) {
  const barSpan = Math.max(placement.highPrice - placement.lowPrice, 1);
  const step = Math.max(barSpan * 0.18, placement.closePrice * 0.0015, 1);
  if (placement.side === "BUY") {
    return Math.max(placement.lowPrice - step * (slotIndex + 1), 0);
  }
  return placement.highPrice + step * (slotIndex + 1);
}

interface OverlayPlacement {
  overlay: ChartOrderOverlay;
  eventTime: UTCTimestamp;
  side: "BUY" | "SELL";
  time: UTCTimestamp;
  barIndex: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  basePrice: number;
}

interface OverlaySeriesInput {
  key: string;
  side: "BUY" | "SELL";
  color: string;
  points: LineData<UTCTimestamp>[];
  markers: SeriesMarker<UTCTimestamp>[];
}

interface OverlaySeriesBinding {
  series: ISeriesApi<"Line", Time>;
  markers: ISeriesMarkersPluginApi<Time>;
}
