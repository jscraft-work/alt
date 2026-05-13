import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  CheckCircle2,
  CircleHelp,
  Loader2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type {
  CycleStatus,
  InstanceDashboard,
  InstanceDashboardSystemStatus,
  InstancePosition,
  SystemStatusCode,
} from "@/lib/api-types";
import type { ApiError } from "@/lib/api";
import {
  formatKrw,
  formatKstDateTime,
  formatNumber,
  formatPercent,
} from "@/lib/format";
import { cn } from "@/lib/utils";

const SERVICE_LABEL: Record<string, string> = {
  marketdata: "시장 데이터",
  news: "뉴스 수집",
  dart: "DART 공시",
  websocket: "웹소켓",
  trader: "트레이더 사이클",
};

export default function InstanceDashboardSection({
  selectedInstanceId,
  selectedInstanceName,
  dashboard,
  isLoading,
  error,
}: {
  selectedInstanceId: string;
  selectedInstanceName: string | null;
  dashboard: InstanceDashboard | null;
  isLoading: boolean;
  error: ApiError | null;
}) {
  const headingName = dashboard?.instance.name ?? selectedInstanceName ?? selectedInstanceId;

  if (isLoading && !dashboard) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>인스턴스 상세</CardTitle>
          <CardDescription>{headingName} 상세 정보를 불러오는 중입니다.</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          실제 인스턴스 상세 API를 조회하고 있습니다.
        </CardContent>
      </Card>
    );
  }

  if (error && !dashboard) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>인스턴스 상세</CardTitle>
          <CardDescription>{headingName} 상세 정보를 가져오지 못했습니다.</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-2 text-sm text-destructive">
          <AlertTriangle className="size-4" />
          {error.message}
        </CardContent>
      </Card>
    );
  }

  if (!dashboard) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>인스턴스 상세</CardTitle>
          <CardDescription>표시할 상세 데이터가 없습니다.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  const recentOrderCount = dashboard.recentOrders.length;

  return (
    <section aria-labelledby="instance-dashboard-heading" className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h2 id="instance-dashboard-heading" className="text-base font-semibold">
          인스턴스 상세
        </h2>
        <p className="text-sm text-muted-foreground">
          {dashboard.instance.name}의 자산, 보유 종목, 최근 활동을 표시합니다.
        </p>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-warning">
          <AlertTriangle className="size-4 shrink-0" />
          최근 갱신에 실패해 마지막 성공값을 유지하고 있습니다. {error.message}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard label="총 자산" value={formatKrw(dashboard.portfolio.totalAssetAmount)} />
        <KpiCard label="현금" value={formatKrw(dashboard.portfolio.cashAmount)} />
        <KpiCard
          label="오늘 실현손익"
          value={formatKrw(dashboard.portfolio.realizedPnlToday)}
          emphasize={
            dashboard.portfolio.realizedPnlToday > 0
              ? "profit"
              : dashboard.portfolio.realizedPnlToday < 0
                ? "loss"
                : undefined
          }
        />
        <KpiCard
          label="최근 주문"
          value={`${formatNumber(recentOrderCount)}건`}
          helper={recentOrderCount === 0 ? "주문 없음" : "최근 활동 기준"}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(320px,1fr)]">
        <PositionsCard positions={dashboard.positions} />
        <RecentActivityCard dashboard={dashboard} />
      </div>
    </section>
  );
}

function KpiCard({
  label,
  value,
  helper,
  emphasize,
}: {
  label: string;
  value: string;
  helper?: string;
  emphasize?: "profit" | "loss";
}) {
  return (
    <Card>
      <CardHeader className="gap-1">
        <CardDescription>{label}</CardDescription>
        <CardTitle
          className={cn(
            "text-2xl tabular-nums",
            emphasize === "profit" && "text-profit",
            emphasize === "loss" && "text-loss",
          )}
        >
          {value}
        </CardTitle>
      </CardHeader>
      {helper && (
        <CardContent className="pt-0 text-xs text-muted-foreground">{helper}</CardContent>
      )}
    </Card>
  );
}

function PositionsCard({ positions }: { positions: InstancePosition[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>보유 종목</CardTitle>
        <CardDescription>
          {positions.length === 0
            ? "현재 보유 중인 종목이 없습니다."
            : `${positions.length}개 종목을 보유 중입니다.`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {positions.length === 0 ? (
          <p className="text-sm text-muted-foreground">미청산 포지션이 없습니다.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>종목명</TableHead>
                <TableHead>종목코드</TableHead>
                <TableHead className="text-right">수량</TableHead>
                <TableHead className="text-right">매수단가</TableHead>
                <TableHead className="text-right">현재가</TableHead>
                <TableHead className="text-right">평가손익</TableHead>
                <TableHead className="text-right">수익률</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {positions.map((position) => (
                <TableRow key={position.symbolCode}>
                  <TableCell className="max-w-[180px]">
                    <div className="truncate font-medium">
                      {position.symbolName || position.symbolCode}
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{position.symbolCode}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatNumber(position.quantity)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatKrw(position.avgBuyPrice)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatKrw(position.lastMarkPrice)}
                  </TableCell>
                  <TableCell
                    className={cn(
                      "text-right tabular-nums",
                      position.unrealizedPnl > 0 && "text-profit",
                      position.unrealizedPnl < 0 && "text-loss",
                    )}
                  >
                    {formatKrw(position.unrealizedPnl)}
                  </TableCell>
                  <TableCell
                    className={cn(
                      "text-right tabular-nums",
                      position.unrealizedPnl > 0 && "text-profit",
                      position.unrealizedPnl < 0 && "text-loss",
                    )}
                  >
                    {formatPositionReturn(position)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function RecentActivityCard({ dashboard }: { dashboard: InstanceDashboard }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>최근 활동</CardTitle>
        <CardDescription>
          최근 판단, 주문, 시스템 신호를 함께 확인합니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <section className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-medium">최근 판단</h3>
            {dashboard.latestDecision && (
              <CycleStatusBadge code={dashboard.latestDecision.cycleStatus} />
            )}
          </div>
          {dashboard.latestDecision ? (
            <div className="rounded-lg border bg-muted/20 p-3">
              <p className="text-sm font-medium">
                {dashboard.latestDecision.summary ?? "판단 요약이 없습니다."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                시작 시각: {formatKstDateTime(dashboard.latestDecision.cycleStartedAt)}
              </p>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">최근 판단 이력이 없습니다.</p>
          )}
        </section>

        <section className="flex flex-col gap-3 border-t pt-5">
          <h3 className="text-sm font-medium">최근 주문</h3>
          {dashboard.recentOrders.length === 0 ? (
            <p className="text-sm text-muted-foreground">표시할 최근 주문이 없습니다.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {dashboard.recentOrders.map((order) => (
                <li
                  key={order.tradeOrderId}
                  className="rounded-lg border bg-muted/20 p-3"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{order.symbolCode}</span>
                    <OrderSideBadge side={order.side} />
                    <Badge variant="outline">{order.orderStatus.toUpperCase()}</Badge>
                  </div>
                  <div className="mt-2 grid grid-cols-1 gap-1 text-xs text-muted-foreground sm:grid-cols-2">
                    <span>주문 시각: {formatKstDateTime(order.requestedAt)}</span>
                    <span>수량: {formatNumber(order.requestedQuantity)}</span>
                    <span>
                      주문가:{" "}
                      {order.requestedPrice === null
                        ? "시장가"
                        : formatKrw(order.requestedPrice)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="flex flex-col gap-3 border-t pt-5">
          <h3 className="text-sm font-medium">시스템 신호</h3>
          {dashboard.systemStatus.length === 0 ? (
            <p className="text-sm text-muted-foreground">표시할 상태 정보가 없습니다.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {dashboard.systemStatus.map((item) => (
                <SystemSignalRow key={item.serviceName} item={item} />
              ))}
            </ul>
          )}
        </section>
      </CardContent>
    </Card>
  );
}

function SystemSignalRow({ item }: { item: InstanceDashboardSystemStatus }) {
  const label = SERVICE_LABEL[item.serviceName] ?? item.serviceName;

  return (
    <li className="flex items-center justify-between gap-3 rounded-lg border bg-muted/20 px-3 py-2">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">{label}</p>
        <p className="truncate text-xs text-muted-foreground">
          발생 시각: {formatKstDateTime(item.occurredAt)}
        </p>
        {item.message && (
          <p className="truncate text-xs text-muted-foreground">{item.message}</p>
        )}
      </div>
      <SystemStatusBadge code={item.statusCode} />
    </li>
  );
}

function OrderSideBadge({ side }: { side: "BUY" | "SELL" }) {
  const Icon = side === "BUY" ? ArrowUpRight : ArrowDownLeft;

  return (
    <Badge
      variant="outline"
      className={cn(side === "BUY" ? "text-profit" : "text-loss")}
    >
      <Icon className="mr-1 size-3" />
      {side === "BUY" ? "매수" : "매도"}
    </Badge>
  );
}

function CycleStatusBadge({ code }: { code: CycleStatus }) {
  switch (code) {
    case "EXECUTE":
      return <Badge className="bg-profit text-white hover:bg-profit/90">EXECUTE</Badge>;
    case "HOLD":
      return <Badge variant="secondary">HOLD</Badge>;
    default:
      return <Badge variant="destructive">FAILED</Badge>;
  }
}

function SystemStatusBadge({ code }: { code: SystemStatusCode }) {
  switch (code) {
    case "ok":
      return (
        <Badge variant="secondary" className="gap-1">
          <CheckCircle2 className="size-3" /> 정상
        </Badge>
      );
    case "delayed":
      return (
        <Badge variant="outline" className="gap-1 text-warning">
          <AlertTriangle className="size-3" /> 지연
        </Badge>
      );
    case "down":
      return (
        <Badge variant="destructive" className="gap-1">
          <AlertTriangle className="size-3" /> 중단
        </Badge>
      );
    default:
      return (
        <Badge variant="outline" className="gap-1">
          <CircleHelp className="size-3" /> 알 수 없음
        </Badge>
      );
  }
}

function formatPositionReturn(position: InstancePosition) {
  const costBasis = position.avgBuyPrice * position.quantity;
  if (costBasis <= 0) {
    return "-";
  }
  return formatPercent((position.unrealizedPnl / costBasis) * 100);
}
