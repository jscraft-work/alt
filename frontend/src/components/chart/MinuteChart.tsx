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
  className?: string;
  isLoadingMoreHistory?: boolean;
  onRequestOlderHistory?: () => void;
}

export default function MinuteChart({
  bars,
  overlays,
  className,
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
  const markerData = useMemo(
    () => overlays.map(toOrderMarker),
    [overlays],
  );

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const chart = createChart(containerRef.current, {
      autoSize: true,
      height: 420,
      layout: {
        background: {
          type: ColorType.Solid,
          color: "transparent",
        },
        textColor: "hsl(var(--muted-foreground))",
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
        borderColor: "hsl(var(--border))",
        scaleMargins: {
          top: 0.1,
          bottom: 0.25,
        },
      },
      timeScale: {
        borderColor: "hsl(var(--border))",
        barSpacing: 9,
        minBarSpacing: 2.5,
        rightOffset: 6,
        tickMarkFormatter: formatTickMark,
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: {
        vertLine: {
          color: "hsl(var(--border))",
          labelBackgroundColor: "hsl(var(--foreground))",
        },
        horzLine: {
          color: "hsl(var(--border))",
          labelBackgroundColor: "hsl(var(--foreground))",
        },
      },
      grid: {
        vertLines: {
          color: "hsl(var(--border) / 0.45)",
        },
        horzLines: {
          color: "hsl(var(--border) / 0.45)",
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
      upColor: "hsl(var(--loss))",
      downColor: "hsl(var(--profit))",
      borderVisible: false,
      wickUpColor: "hsl(var(--loss))",
      wickDownColor: "hsl(var(--profit))",
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

    const markerPlugin = createSeriesMarkers(candleSeries, []);

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
  }, [isLoadingMoreHistory, onRequestOlderHistory]);

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
  }, [bars, candleData, markerData, volumeData]);

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
        매수 마커는 위 화살표, 매도 마커는 아래 화살표로 표시됩니다. 차트
        왼쪽 끝으로 이동하면 과거 구간을 자동으로 더 불러옵니다.
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
    color: isUp ? "hsl(var(--loss) / 0.35)" : "hsl(var(--profit) / 0.35)",
  };
}

function toOrderMarker(overlay: ChartOrderOverlay): SeriesMarker<UTCTimestamp> {
  const markerTime = toUtcTimestamp(overlay.filledAt ?? overlay.requestedAt);
  const side = overlay.side === "BUY" ? "BUY" : "SELL";
  const isBuy = side === "BUY";
  return {
    time: markerTime,
    position: isBuy ? "belowBar" : "aboveBar",
    shape: isBuy ? "arrowUp" : "arrowDown",
    color: isBuy ? "hsl(var(--loss))" : "hsl(var(--profit))",
    text: side,
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
