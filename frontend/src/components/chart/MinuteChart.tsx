import type { ChartOrderOverlay, MinuteBar } from "@/lib/api-types";
import { formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";

const VIEWBOX_WIDTH = 960;
const VIEWBOX_HEIGHT = 420;
const PRICE_CHART_HEIGHT = 238;
const VOLUME_CHART_HEIGHT = 76;
const CHART_GAP = 34;
const MARGIN_TOP = 24;
const MARGIN_RIGHT = 72;
const MARGIN_BOTTOM = 42;
const MARGIN_LEFT = 72;
const GRID_TICK_COUNT = 5;
const TIME_TICK_COUNT = 6;

interface MinuteChartProps {
  bars: MinuteBar[];
  overlays: ChartOrderOverlay[];
  className?: string;
}

export default function MinuteChart({
  bars,
  overlays,
  className,
}: MinuteChartProps) {
  const chartLeft = MARGIN_LEFT;
  const chartTop = MARGIN_TOP;
  const chartWidth = VIEWBOX_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
  const priceChartBottom = chartTop + PRICE_CHART_HEIGHT;
  const volumeChartTop = priceChartBottom + CHART_GAP;
  const volumeChartBottom = volumeChartTop + VOLUME_CHART_HEIGHT;
  const timeTickIndexes = createTimeTickIndexes(bars.length, TIME_TICK_COUNT);
  const overlayPrices = overlays
    .map((overlay) => getOverlayPrice(overlay, bars))
    .filter((price): price is number => price !== null);

  const allPrices = [
    ...bars.flatMap((bar) => [
      bar.openPrice,
      bar.highPrice,
      bar.lowPrice,
      bar.closePrice,
    ]),
    ...overlayPrices,
  ];

  const rawMinPrice = Math.min(...allPrices);
  const rawMaxPrice = Math.max(...allPrices);
  const pricePadding =
    rawMinPrice === rawMaxPrice
      ? Math.max(rawMaxPrice * 0.01, 1)
      : (rawMaxPrice - rawMinPrice) * 0.08;
  const minPrice = rawMinPrice - pricePadding;
  const maxPrice = rawMaxPrice + pricePadding;
  const priceRange = Math.max(maxPrice - minPrice, 1);
  const volumeMax = Math.max(...bars.map((bar) => bar.volume), 1);
  const bodyWidth = Math.min(14, Math.max(chartWidth / bars.length - 2, 3));
  const firstBarTime = new Date(bars[0].barTime).getTime();
  const lastBarTime = new Date(bars[bars.length - 1].barTime).getTime();

  const priceToY = (price: number) =>
    priceChartBottom - ((price - minPrice) / priceRange) * PRICE_CHART_HEIGHT;
  const volumeToY = (volume: number) =>
    volumeChartBottom - (volume / volumeMax) * VOLUME_CHART_HEIGHT;
  const xForIndex = (index: number) =>
    chartLeft + ((index + 0.5) / bars.length) * chartWidth;

  const xForTime = (isoValue: string) => {
    const time = new Date(isoValue).getTime();

    if (
      !Number.isFinite(time) ||
      !Number.isFinite(firstBarTime) ||
      !Number.isFinite(lastBarTime)
    ) {
      return xForIndex(0);
    }

    if (lastBarTime <= firstBarTime) {
      return xForIndex(findNearestBarIndex(bars, isoValue));
    }

    const ratio = clamp((time - firstBarTime) / (lastBarTime - firstBarTime), 0, 1);
    return chartLeft + ratio * chartWidth;
  };

  return (
    <div
      className={cn(
        "rounded-xl border border-border/70 bg-muted/20 p-2 sm:p-3",
        className,
      )}
    >
      <svg
        viewBox={`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`}
        className="h-auto w-full"
        role="img"
        aria-label="분봉 차트와 주문 오버레이"
      >
        <rect
          x={chartLeft}
          y={chartTop}
          width={chartWidth}
          height={PRICE_CHART_HEIGHT}
          rx="12"
          fill="var(--card)"
          stroke="var(--border)"
        />
        <rect
          x={chartLeft}
          y={volumeChartTop}
          width={chartWidth}
          height={VOLUME_CHART_HEIGHT}
          rx="12"
          fill="var(--card)"
          stroke="var(--border)"
        />

        {createPriceTicks(minPrice, maxPrice, GRID_TICK_COUNT).map((price) => {
          const y = priceToY(price);
          return (
            <g key={`price-${price}`}>
              <line
                x1={chartLeft}
                x2={chartLeft + chartWidth}
                y1={y}
                y2={y}
                stroke="var(--border)"
                strokeDasharray="4 6"
              />
              <text
                x={chartLeft - 12}
                y={y + 4}
                fill="var(--muted-foreground)"
                fontSize="12"
                textAnchor="end"
              >
                {formatNumber(Math.round(price))}
              </text>
            </g>
          );
        })}

        {timeTickIndexes.map((index) => {
          const x = xForIndex(index);
          return (
            <g key={`time-${bars[index].barTime}`}>
              <line
                x1={x}
                x2={x}
                y1={chartTop}
                y2={priceChartBottom}
                stroke="var(--border)"
                strokeDasharray="3 8"
              />
              <text
                x={x}
                y={VIEWBOX_HEIGHT - MARGIN_BOTTOM + 18}
                fill="var(--muted-foreground)"
                fontSize="12"
                textAnchor="middle"
              >
                {formatTimeLabel(bars[index].barTime)}
              </text>
            </g>
          );
        })}

        {bars.map((bar, index) => {
          const x = xForIndex(index);
          const openY = priceToY(bar.openPrice);
          const closeY = priceToY(bar.closePrice);
          const highY = priceToY(bar.highPrice);
          const lowY = priceToY(bar.lowPrice);
          const volumeY = volumeToY(bar.volume);
          const bodyTop = Math.min(openY, closeY);
          const bodyHeight = Math.max(Math.abs(openY - closeY), 1.5);
          const isUp = bar.closePrice >= bar.openPrice;
          const candleColor = isUp ? "var(--loss)" : "var(--profit)";

          return (
            <g key={bar.barTime}>
              <line
                x1={x}
                x2={x}
                y1={highY}
                y2={lowY}
                stroke={candleColor}
                strokeWidth="1.5"
              />
              <rect
                x={x - bodyWidth / 2}
                y={bodyTop}
                width={bodyWidth}
                height={bodyHeight}
                rx="1.5"
                fill={candleColor}
                opacity={isUp ? 0.85 : 0.95}
              />
              <rect
                x={x - bodyWidth / 2}
                y={volumeY}
                width={bodyWidth}
                height={Math.max(volumeChartBottom - volumeY, 1)}
                rx="1.5"
                fill={candleColor}
                opacity="0.35"
              />
            </g>
          );
        })}

        {overlays.map((overlay) => {
          const x = xForTime(overlay.filledAt ?? overlay.requestedAt);
          const price = getOverlayPrice(overlay, bars);
          const y = price === null ? priceChartBottom - 12 : priceToY(price);
          const color = overlay.side === "BUY" ? "var(--loss)" : "var(--profit)";
          const filled = isFilledOrder(overlay.orderStatus);

          return (
            <g key={overlay.tradeOrderId}>
              <line
                x1={x}
                x2={x}
                y1={chartTop}
                y2={priceChartBottom}
                stroke={color}
                strokeWidth="1"
                strokeDasharray="4 5"
                opacity="0.4"
              />
              <path
                d={buildMarkerPath(x, y, overlay.side === "BUY" ? "up" : "down")}
                fill={filled ? color : "var(--card)"}
                stroke={color}
                strokeWidth="2"
              />
              <text
                x={x}
                y={overlay.side === "BUY" ? y - 12 : y + 22}
                fill={color}
                fontSize="11"
                fontWeight="600"
                textAnchor="middle"
              >
                {overlay.side}
              </text>
            </g>
          );
        })}

        <text
          x={chartLeft}
          y={VIEWBOX_HEIGHT - 12}
          fill="var(--muted-foreground)"
          fontSize="12"
        >
          주문 마커: 매수는 위 삼각형, 매도는 아래 삼각형, 미체결 주문은 속이 빈 형태로 표시됩니다.
        </text>
      </svg>
    </div>
  );
}

function createPriceTicks(minPrice: number, maxPrice: number, count: number) {
  if (count <= 1) {
    return [maxPrice];
  }

  const ticks: number[] = [];
  const step = (maxPrice - minPrice) / (count - 1);

  for (let index = 0; index < count; index += 1) {
    ticks.push(maxPrice - step * index);
  }

  return ticks;
}

function createTimeTickIndexes(length: number, count: number) {
  if (length <= 1) {
    return [0];
  }

  const tickIndexes = new Set<number>([0, length - 1]);

  for (let index = 1; index < count - 1; index += 1) {
    tickIndexes.add(Math.round(((length - 1) * index) / (count - 1)));
  }

  return Array.from(tickIndexes).sort((left, right) => left - right);
}

function formatTimeLabel(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "--:--";
  }

  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });

  return formatter.format(date);
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function buildMarkerPath(x: number, y: number, direction: "up" | "down") {
  const size = 8;

  if (direction === "up") {
    return `M ${x} ${y - size} L ${x + size} ${y + size} L ${x - size} ${y + size} Z`;
  }

  return `M ${x} ${y + size} L ${x + size} ${y - size} L ${x - size} ${y - size} Z`;
}

function isFilledOrder(status: string) {
  return status === "filled" || status === "partial";
}

function getOverlayPrice(overlay: ChartOrderOverlay, bars: MinuteBar[]) {
  if (overlay.avgFilledPrice !== null) {
    return overlay.avgFilledPrice;
  }

  if (overlay.requestedPrice !== null) {
    return overlay.requestedPrice;
  }

  const nearestBar = bars[findNearestBarIndex(bars, overlay.filledAt ?? overlay.requestedAt)];
  return nearestBar?.closePrice ?? null;
}

function findNearestBarIndex(bars: MinuteBar[], isoValue: string) {
  const targetTime = new Date(isoValue).getTime();

  if (!Number.isFinite(targetTime) || bars.length === 0) {
    return 0;
  }

  let nearestIndex = 0;
  let nearestGap = Number.POSITIVE_INFINITY;

  for (let index = 0; index < bars.length; index += 1) {
    const gap = Math.abs(new Date(bars[index].barTime).getTime() - targetTime);
    if (gap < nearestGap) {
      nearestGap = gap;
      nearestIndex = index;
    }
  }

  return nearestIndex;
}
