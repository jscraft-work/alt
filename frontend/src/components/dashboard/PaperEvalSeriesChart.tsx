import { useEffect, useMemo, useRef } from "react";
import {
  AreaSeries,
  ColorType,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type Time,
  type UTCTimestamp,
  createChart,
} from "lightweight-charts";
import type { PaperEvalDailyPoint } from "@/lib/api-types";
import { toFiniteNumber } from "@/lib/format";
import { cn } from "@/lib/utils";

interface PaperEvalSeriesChartProps {
  points: PaperEvalDailyPoint[];
  className?: string;
  /**
   * `cumulative`: 누적 net_pnl_pct 추이 (peak 대비 drawdown 직관)
   * `daily`: 일별 net_pnl_pct
   */
  mode?: "cumulative" | "daily";
}

/**
 * paper_trade_match 일별 net_pnl_pct 의 area chart.
 *
 * - lightweight-charts AreaSeries 사용
 * - x축: KST 일자, y축: %
 * - cumulative 모드는 매일의 net_pnl_pct 를 더한 누적 곡선
 */
export default function PaperEvalSeriesChart({
  points,
  className,
  mode = "cumulative",
}: PaperEvalSeriesChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<"Area", Time> | null>(null);
  const zeroLineRef = useRef<ISeriesApi<"Line", Time> | null>(null);

  const data = useMemo(() => buildSeriesData(points, mode), [points, mode]);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const palette = resolveChartPalette();
    const chart = createChart(containerRef.current, {
      autoSize: true,
      height: 280,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: palette.mutedForeground,
        attributionLogo: false,
      },
      localization: {
        locale: "ko-KR",
        priceFormatter: (price: number) => `${price.toFixed(2)}%`,
      },
      rightPriceScale: {
        borderColor: palette.border,
        scaleMargins: { top: 0.15, bottom: 0.15 },
      },
      timeScale: {
        borderColor: palette.border,
        timeVisible: false,
        secondsVisible: false,
        tickMarkFormatter: formatTickMark,
      },
      grid: {
        vertLines: { color: withAlpha(palette.border, 0.4) },
        horzLines: { color: withAlpha(palette.border, 0.4) },
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
      handleScroll: false,
      handleScale: false,
    });

    const series = chart.addSeries(AreaSeries, {
      lineColor: palette.profit,
      topColor: withAlpha(palette.profit, 0.35),
      bottomColor: withAlpha(palette.profit, 0.0),
      lineWidth: 2,
      lastValueVisible: true,
      priceLineVisible: false,
    });

    const zeroLine = chart.addSeries(LineSeries, {
      color: withAlpha(palette.mutedForeground, 0.6),
      lineWidth: 1,
      lineStyle: 2,
      lastValueVisible: false,
      priceLineVisible: false,
    });

    chartRef.current = chart;
    seriesRef.current = series;
    zeroLineRef.current = zeroLine;

    return () => {
      seriesRef.current = null;
      zeroLineRef.current = null;
      chartRef.current = null;
      chart.remove();
    };
  }, []);

  useEffect(() => {
    if (!seriesRef.current || !zeroLineRef.current || !chartRef.current) {
      return;
    }
    seriesRef.current.setData(data);
    zeroLineRef.current.setData(
      data.length === 0
        ? []
        : [
            { time: data[0].time, value: 0 },
            { time: data[data.length - 1].time, value: 0 },
          ],
    );
    if (data.length > 0) {
      chartRef.current.timeScale().fitContent();
    }
  }, [data]);

  return (
    <div
      className={cn(
        "rounded-xl border border-border/70 bg-muted/20 p-2 sm:p-3",
        className,
      )}
    >
      <div
        ref={containerRef}
        className="h-[280px] w-full overflow-hidden rounded-lg bg-card"
      />
    </div>
  );
}

function buildSeriesData(
  points: PaperEvalDailyPoint[],
  mode: "cumulative" | "daily",
): { time: UTCTimestamp; value: number }[] {
  if (points.length === 0) {
    return [];
  }
  const sorted = [...points].sort((a, b) =>
    a.businessDate.localeCompare(b.businessDate),
  );
  let cumulative = 0;
  return sorted.map((p) => {
    const raw = toFiniteNumber(p.netPnlPct) ?? 0;
    // 백엔드 net_pnl_pct 는 ratio (예: 0.0123). UI 표시는 % 단위.
    const percent = raw * 100;
    if (mode === "cumulative") {
      cumulative += percent;
      return { time: toDayTimestamp(p.businessDate), value: cumulative };
    }
    return { time: toDayTimestamp(p.businessDate), value: percent };
  });
}

function toDayTimestamp(isoDate: string): UTCTimestamp {
  // businessDate 는 KST 일자. lightweight-charts 의 일봉은 UTC 기준 자정 timestamp 를 받음.
  // KST 00:00 = UTC 전날 15:00 이지만, 일자 라벨만 보이면 되므로 ISO 그대로 UTC 자정으로 환산.
  const date = new Date(`${isoDate}T00:00:00Z`);
  return Math.floor(date.getTime() / 1000) as UTCTimestamp;
}

function formatTickMark(time: Time) {
  if (typeof time !== "number") {
    return null;
  }
  const date = new Date(time * 1000);
  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "UTC",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(date);
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

function resolveCssColor(token: string, fallback: string) {
  if (typeof document === "undefined") {
    return fallback;
  }
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
