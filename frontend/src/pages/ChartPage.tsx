import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { AlertCircle, Loader2, RefreshCw } from "lucide-react";
import MinuteChart from "@/components/chart/MinuteChart";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAssetMasters } from "@/hooks/use-asset-masters";
import { useOrderOverlays, useMinuteBars } from "@/hooks/use-charts";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";
import { useSelectableStrategyInstances } from "@/hooks/use-strategy-instances";
import type { ChartOrderOverlay, MinuteBar } from "@/lib/api-types";
import { formatKrw, formatKstDateTime, formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";

const ALL_INSTANCES_VALUE = "__all__";

interface ChartFilters {
  symbolCode: string;
  date: string;
}

export default function ChartPage() {
  const { selectedInstanceId, setSelectedInstanceId } =
    useStrategyInstanceSelection();
  const {
    data: selectableInstances,
    isLoading: isInstancesLoading,
    error: selectableInstancesError,
  } = useSelectableStrategyInstances();
  const { data: assets, isLoading: isAssetsLoading } = useAssetMasters({
    hidden: false,
  });
  const [filters, setFilters] = useState<ChartFilters>(createInitialFilters);
  const [appliedFilters, setAppliedFilters] = useState<ChartFilters | null>(null);

  // 페이지 진입 시 첫 종목 + 오늘 날짜로 자동 조회.
  useEffect(() => {
    if (appliedFilters !== null) return;
    const first = assets?.[0];
    if (!first) return;
    const next = {
      symbolCode: first.symbolCode,
      date: getTodayInKst(),
    };
    setFilters(next);
    setAppliedFilters(next);
  }, [assets, appliedFilters]);

  const shouldFetch = appliedFilters !== null;
  const selectedInstance =
    selectableInstances?.find((instance) => instance.id === selectedInstanceId) ??
    null;

  const applySymbol = (symbolCode: string) => {
    const date = filters.date || getTodayInKst();
    const next = { symbolCode, date };
    setFilters(next);
    setAppliedFilters(next);
  };

  const minuteBarsQuery = useMinuteBars(
    {
      symbolCode: appliedFilters?.symbolCode ?? "",
      date: appliedFilters?.date ?? "",
    },
    shouldFetch,
  );
  const orderOverlaysQuery = useOrderOverlays(
    {
      symbolCode: appliedFilters?.symbolCode ?? "",
      date: appliedFilters?.date ?? "",
      strategyInstanceId: selectedInstanceId,
    },
    shouldFetch,
  );

  const bars = minuteBarsQuery.data?.bars ?? [];
  const overlays = orderOverlaysQuery.data ?? [];
  const barSummary = bars.length > 0 ? summarizeBars(bars) : null;
  const isRefreshing =
    (minuteBarsQuery.isFetching && !minuteBarsQuery.isPending) ||
    (orderOverlaysQuery.isFetching && !orderOverlaysQuery.isPending);
  const canSubmit = filters.symbolCode.trim() !== "" && filters.date !== "";

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const nextFilters = {
      symbolCode: normalizeSymbolCode(filters.symbolCode),
      date: filters.date,
    };

    setFilters(nextFilters);
    setAppliedFilters(nextFilters);
  };

  const onReset = () => {
    setFilters(createInitialFilters());
    setAppliedFilters(null);
  };

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold">차트</h1>
          <p className="text-sm text-muted-foreground">
            종목별 1분봉 위에 전략 주문 시점을 겹쳐 보고, 특정 인스턴스 오버레이만
            별도로 좁혀서 확인할 수 있습니다.
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            void minuteBarsQuery.refetch();
            void orderOverlaysQuery.refetch();
          }}
          disabled={!shouldFetch || minuteBarsQuery.isPending}
        >
          {isRefreshing ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <RefreshCw className="size-4" />
          )}
          새로고침
        </Button>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>조회 필터</CardTitle>
          <CardDescription>
            종목을 빠른 선택 버튼으로 고르거나 직접 입력하고, 전략 인스턴스 선택은
            주문 오버레이 조회 조건으로 반영됩니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label>종목 빠른 선택</Label>
            {isAssetsLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" />
                종목 목록을 불러오는 중...
              </div>
            ) : !assets || assets.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                등록된 종목이 없습니다.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {assets.map((asset) => {
                  const active =
                    appliedFilters?.symbolCode === asset.symbolCode;
                  return (
                    <Button
                      key={asset.symbolCode}
                      type="button"
                      variant={active ? "default" : "outline"}
                      size="sm"
                      onClick={() => applySymbol(asset.symbolCode)}
                    >
                      {asset.symbolName ?? asset.symbolCode}
                      {asset.symbolName ? (
                        <span className="text-xs opacity-80">
                          {asset.symbolCode}
                        </span>
                      ) : null}
                    </Button>
                  );
                })}
              </div>
            )}
          </div>

          <form className="flex flex-col gap-4" onSubmit={onSubmit}>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-4">
              <FilterField label="종목 코드" htmlFor="chart-symbol">
                <Input
                  id="chart-symbol"
                  value={filters.symbolCode}
                  onChange={(event) => {
                    setFilters((prev) => ({
                      ...prev,
                      symbolCode: event.target.value,
                    }));
                  }}
                  placeholder="예: 005930"
                  autoComplete="off"
                />
              </FilterField>

              <FilterField label="거래일" htmlFor="chart-date">
                <Input
                  id="chart-date"
                  type="date"
                  value={filters.date}
                  onChange={(event) => {
                    setFilters((prev) => ({ ...prev, date: event.target.value }));
                  }}
                />
              </FilterField>

              <FilterField label="전략 인스턴스" htmlFor="chart-instance">
                <div className="relative">
                  <select
                    id="chart-instance"
                    className={cn(
                      "h-8 w-full rounded-lg border border-input bg-background px-3 pr-9 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
                      isInstancesLoading && "text-muted-foreground",
                    )}
                    value={selectedInstanceId ?? ALL_INSTANCES_VALUE}
                    onChange={(event) => {
                      const nextValue = event.target.value;
                      setSelectedInstanceId(
                        nextValue === ALL_INSTANCES_VALUE ? null : nextValue,
                      );
                    }}
                    disabled={isInstancesLoading}
                  >
                    <option value={ALL_INSTANCES_VALUE}>전체 전략</option>
                    {(selectableInstances ?? []).map((instance) => (
                      <option key={instance.id} value={instance.id}>
                        {instance.name}
                      </option>
                    ))}
                  </select>
                  {isInstancesLoading && (
                    <Loader2 className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />
                  )}
                </div>
              </FilterField>

              <div className="flex items-end gap-2">
                <Button type="submit" disabled={!canSubmit}>
                  조회
                </Button>
                <Button type="button" variant="outline" onClick={onReset}>
                  초기화
                </Button>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <Badge variant="outline">
                오버레이 기준 {selectedInstance?.name ?? "전체 전략"}
              </Badge>
              <span>
                전역 선택 인스턴스가 있으면 주문 오버레이에 기본 적용됩니다.
              </span>
              {selectableInstancesError ? (
                <span className="text-destructive">
                  인스턴스 목록 조회 실패: {selectableInstancesError.message}
                </span>
              ) : null}
            </div>
          </form>
        </CardContent>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(280px,0.75fr)]">
        <Card className="min-h-[560px]">
          <CardHeader>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-1">
                <CardTitle>분봉 차트</CardTitle>
                <CardDescription>
                  캔들 색상은 국내 시장 관례에 맞춰 상승 파랑, 하락 빨강으로
                  표시합니다.
                </CardDescription>
              </div>
              {appliedFilters && (
                <div className="flex flex-wrap justify-end gap-2">
                  <Badge variant="outline">{appliedFilters.symbolCode}</Badge>
                  <Badge variant="outline">{appliedFilters.date}</Badge>
                  <Badge variant="outline">
                    주문 {formatNumber(overlays.length)}건
                  </Badge>
                </div>
              )}
            </div>
          </CardHeader>
          <CardContent className="flex h-full flex-col gap-4">
            {!appliedFilters ? (
              <StatePanel
                icon={<AlertCircle className="size-5" />}
                title="조회 조건을 입력하세요"
                description="종목 코드와 날짜를 입력한 뒤 조회하면 분봉과 주문 오버레이를 함께 불러옵니다."
              />
            ) : minuteBarsQuery.isPending ? (
              <StatePanel
                icon={<Loader2 className="size-5 animate-spin" />}
                title="분봉 데이터를 불러오는 중입니다"
                description="시장 데이터와 주문 오버레이를 함께 준비하고 있습니다."
              />
            ) : minuteBarsQuery.error ? (
              <StatePanel
                icon={<AlertCircle className="size-5" />}
                title="분봉 조회에 실패했습니다"
                description={minuteBarsQuery.error.message}
                action={
                  <Button
                    variant="outline"
                    onClick={() => {
                      void minuteBarsQuery.refetch();
                    }}
                  >
                    다시 시도
                  </Button>
                }
              />
            ) : bars.length === 0 ? (
              <StatePanel
                icon={<AlertCircle className="size-5" />}
                title="분봉 데이터가 없습니다"
                description="선택한 날짜에 해당 종목의 1분봉이 비어 있습니다. 다른 거래일 또는 종목 코드를 확인해 주세요."
              />
            ) : (
              <>
                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  <SummaryTile label="고가" value={formatKrw(barSummary?.highPrice)} />
                  <SummaryTile label="저가" value={formatKrw(barSummary?.lowPrice)} />
                  <SummaryTile
                    label="종가"
                    value={formatKrw(barSummary?.closePrice)}
                    emphasis={getChangeTone(barSummary?.closePrice, barSummary?.openPrice)}
                  />
                  <SummaryTile
                    label="누적 거래량"
                    value={formatNumber(barSummary?.totalVolume)}
                  />
                </div>

                <MinuteChart bars={bars} overlays={overlays} />

                {orderOverlaysQuery.error && (
                  <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                    주문 오버레이 조회 실패: {orderOverlaysQuery.error.message}
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>주문 오버레이</CardTitle>
            <CardDescription>
              현재 전역 선택 기준으로 필터링한 주문 목록입니다.
            </CardDescription>
            <CardAction>
              {orderOverlaysQuery.isFetching && !orderOverlaysQuery.isPending ? (
                <Badge variant="outline">갱신 중</Badge>
              ) : null}
            </CardAction>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <SummaryTile
                label="오버레이 인스턴스"
                value={selectedInstance?.name ?? "전체 전략"}
              />
              <SummaryTile
                label="주문 건수"
                value={
                  appliedFilters && !orderOverlaysQuery.isPending
                    ? `${formatNumber(overlays.length)}건`
                    : "-"
                }
              />
            </div>

            {!appliedFilters ? (
              <StatePanel
                compact
                icon={<AlertCircle className="size-4" />}
                title="조회 전입니다"
                description="먼저 종목과 날짜를 조회하면 주문 오버레이 목록이 함께 표시됩니다."
              />
            ) : orderOverlaysQuery.isPending ? (
              <StatePanel
                compact
                icon={<Loader2 className="size-4 animate-spin" />}
                title="주문 오버레이를 불러오는 중입니다"
                description="선택한 전략 인스턴스 기준의 주문 데이터를 확인하고 있습니다."
              />
            ) : orderOverlaysQuery.error ? (
              <StatePanel
                compact
                icon={<AlertCircle className="size-4" />}
                title="주문 오버레이 조회에 실패했습니다"
                description={orderOverlaysQuery.error.message}
              />
            ) : overlays.length === 0 ? (
              <StatePanel
                compact
                icon={<AlertCircle className="size-4" />}
                title="표시할 주문이 없습니다"
                description="해당 조건에서 주문 오버레이가 없으면 분봉만 표시됩니다."
              />
            ) : (
              <div className="overflow-hidden rounded-lg border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>시각</TableHead>
                      <TableHead>구분</TableHead>
                      <TableHead>상태</TableHead>
                      <TableHead className="text-right">가격</TableHead>
                      <TableHead className="text-right">수량</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {overlays.map((overlay) => (
                      <TableRow key={overlay.tradeOrderId}>
                        <TableCell>
                          {formatKstDateTime(overlay.filledAt ?? overlay.requestedAt)}
                        </TableCell>
                        <TableCell>
                          <Badge className={getSideBadgeClassName(overlay.side)}>
                            {overlay.side}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant="outline"
                            className={getStatusBadgeClassName(overlay.orderStatus)}
                          >
                            {overlay.orderStatus}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          {formatKrw(
                            overlay.avgFilledPrice ?? overlay.requestedPrice,
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatNumber(overlay.requestedQuantity)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function FilterField({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}

function SummaryTile({
  label,
  value,
  emphasis,
}: {
  label: string;
  value: string;
  emphasis?: "profit" | "loss" | null;
}) {
  return (
    <div className="rounded-lg border border-border/70 bg-muted/30 px-3 py-2">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p
        className={cn(
          "mt-1 text-base font-semibold",
          emphasis === "profit" && "text-profit",
          emphasis === "loss" && "text-loss",
        )}
      >
        {value}
      </p>
    </div>
  );
}

function StatePanel({
  icon,
  title,
  description,
  action,
  compact = false,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div
      className={cn(
        "flex flex-1 flex-col items-center justify-center rounded-xl border border-dashed border-border/80 bg-muted/20 px-6 text-center",
        compact ? "min-h-[180px] py-8" : "min-h-[420px] py-12",
      )}
    >
      <div className="mb-3 flex size-10 items-center justify-center rounded-full bg-muted text-muted-foreground">
        {icon}
      </div>
      <p className="text-sm font-medium">{title}</p>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">
        {description}
      </p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

function summarizeBars(bars: MinuteBar[]) {
  return bars.reduce(
    (summary, bar, index) => ({
      openPrice: index === 0 ? bar.openPrice : summary.openPrice,
      closePrice: bar.closePrice,
      highPrice: Math.max(summary.highPrice, bar.highPrice),
      lowPrice: Math.min(summary.lowPrice, bar.lowPrice),
      totalVolume: summary.totalVolume + bar.volume,
    }),
    {
      openPrice: bars[0].openPrice,
      closePrice: bars[0].closePrice,
      highPrice: bars[0].highPrice,
      lowPrice: bars[0].lowPrice,
      totalVolume: 0,
    },
  );
}

function createInitialFilters(): ChartFilters {
  return {
    symbolCode: "",
    date: getTodayInKst(),
  };
}

function normalizeSymbolCode(symbolCode: string) {
  return symbolCode.trim().toUpperCase();
}

function getTodayInKst() {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = formatter.formatToParts(new Date());
  const year = parts.find((part) => part.type === "year")?.value ?? "0000";
  const month = parts.find((part) => part.type === "month")?.value ?? "01";
  const day = parts.find((part) => part.type === "day")?.value ?? "01";
  return `${year}-${month}-${day}`;
}

function getChangeTone(closePrice?: number, openPrice?: number) {
  if (closePrice === undefined || openPrice === undefined) {
    return null;
  }
  if (closePrice > openPrice) {
    return "loss";
  }
  if (closePrice < openPrice) {
    return "profit";
  }
  return null;
}

function getSideBadgeClassName(side: ChartOrderOverlay["side"]) {
  return side === "BUY"
    ? "bg-loss text-white hover:bg-loss/90"
    : "bg-profit text-white hover:bg-profit/90";
}

function getStatusBadgeClassName(status: ChartOrderOverlay["orderStatus"]) {
  if (status === "filled") {
    return "border-loss/30 bg-loss/10 text-loss";
  }
  if (status === "partial" || status === "accepted" || status === "requested") {
    return "border-warning/30 bg-warning/10 text-[color:var(--warning)]";
  }
  if (status === "canceled" || status === "rejected" || status === "failed") {
    return "border-destructive/30 bg-destructive/10 text-destructive";
  }
  return "";
}
