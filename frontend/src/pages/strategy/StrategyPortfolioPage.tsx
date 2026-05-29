import { Link, useParams } from "react-router-dom";
import { AlertTriangle, ArrowLeft, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import SettingsHeader from "@/components/settings/SettingsHeader";
import { useInstanceDashboard } from "@/hooks/use-dashboard";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";
import {
  formatKrw,
  formatKstDateTime,
  formatNumber,
  formatPercent,
} from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  InstanceDashboard,
  InstancePosition,
} from "@/lib/api-types";

/**
 * 전략 인스턴스 포트폴리오 페이지 (F4 신규).
 *
 * - 기존 DashboardPage 의 InstanceDashboardSection portfolio 시각화 분리
 * - KPI 4 카드 (총자산, 현금, 오늘 실현손익, 보유종목 수)
 * - 보유 종목 표 (positions)
 * - 최근 주문 표 (recentOrders)
 */
export default function StrategyPortfolioPage() {
  const params = useParams();
  const instanceId = params.id ?? "";
  const instances = useStrategyInstances();
  const dashboard = useInstanceDashboard(instanceId || null);
  const instance = instances.data?.find((row) => row.id === instanceId);

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title={`포트폴리오${instance ? ` · ${instance.name}` : ""}`}
        description="해당 인스턴스의 자산 / 보유 종목 / 최근 주문을 표시합니다. 30초마다 자동 갱신."
        action={
          <Button variant="outline" render={<Link to="/strategy" />}>
            <ArrowLeft className="size-4" /> 인스턴스 목록
          </Button>
        }
      />

      {dashboard.isLoading && !dashboard.data ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 포트폴리오 불러오는 중…
        </div>
      ) : dashboard.error ? (
        <p className="flex items-center gap-2 text-sm text-destructive">
          <AlertTriangle className="size-4" /> {dashboard.error.message}
        </p>
      ) : !dashboard.data ? (
        <p className="text-sm text-muted-foreground">
          포트폴리오 데이터를 찾을 수 없습니다.
        </p>
      ) : (
        <PortfolioContent dashboard={dashboard.data} />
      )}
    </div>
  );
}

function PortfolioContent({ dashboard }: { dashboard: InstanceDashboard }) {
  return (
    <>
      <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard label="총 자산" value={formatKrw(dashboard.portfolio.totalAssetAmount)} />
        <KpiCard label="현금" value={formatKrw(dashboard.portfolio.cashAmount)} />
        <KpiCard
          label="오늘 실현손익"
          value={formatKrw(dashboard.portfolio.realizedPnlToday)}
          tone={
            dashboard.portfolio.realizedPnlToday > 0
              ? "profit"
              : dashboard.portfolio.realizedPnlToday < 0
                ? "loss"
                : undefined
          }
        />
        <KpiCard
          label="보유 종목"
          value={`${dashboard.positions.length} 종목`}
          hint={`최근 주문 ${dashboard.recentOrders.length}건`}
        />
      </section>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">보유 종목</CardTitle>
          <CardDescription>
            {dashboard.positions.length === 0
              ? "현재 보유 중인 종목이 없습니다."
              : `${dashboard.positions.length}개 종목 보유 중. 마크 가격 기준 평가손익.`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {dashboard.positions.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              미청산 포지션이 없습니다.
            </p>
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
                {dashboard.positions.map((position) => (
                  <TableRow key={position.symbolCode}>
                    <TableCell className="max-w-[180px]">
                      <div className="truncate font-medium">
                        {position.symbolName || position.symbolCode}
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {position.symbolCode}
                    </TableCell>
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

      <Card>
        <CardHeader>
          <CardTitle className="text-base">최근 주문</CardTitle>
          <CardDescription>
            최근 trade_order row. 전체 페이지네이션은 매매 이력에서 확인.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {dashboard.recentOrders.length === 0 ? (
            <p className="text-sm text-muted-foreground">최근 주문이 없습니다.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>종목</TableHead>
                  <TableHead>side</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead className="text-right">수량</TableHead>
                  <TableHead className="text-right">주문가</TableHead>
                  <TableHead>주문 시각</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {dashboard.recentOrders.map((order) => (
                  <TableRow key={order.tradeOrderId}>
                    <TableCell className="font-mono text-xs">
                      {order.symbolCode}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={cn(
                          order.side === "BUY" ? "text-profit" : "text-loss",
                        )}
                      >
                        {order.side === "BUY" ? "매수" : "매도"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {order.orderStatus.toUpperCase()}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatNumber(order.requestedQuantity)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {order.requestedPrice === null
                        ? "시장가"
                        : formatKrw(order.requestedPrice)}
                    </TableCell>
                    <TableCell className="text-xs">
                      {formatKstDateTime(order.requestedAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </>
  );
}

function KpiCard({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: "profit" | "loss";
}) {
  return (
    <Card>
      <CardHeader className="pb-1">
        <CardDescription className="text-xs uppercase tracking-wide">
          {label}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div
          className={cn(
            "text-2xl font-semibold tabular-nums",
            tone === "profit" && "text-profit",
            tone === "loss" && "text-loss",
          )}
        >
          {value}
        </div>
        {hint ? (
          <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function formatPositionReturn(position: InstancePosition) {
  const costBasis = position.avgBuyPrice * position.quantity;
  if (costBasis <= 0) return "-";
  return formatPercent((position.unrealizedPnl / costBasis) * 100);
}
