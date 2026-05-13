import { AlertTriangle, Loader2 } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useStrategyOverview } from "@/hooks/use-dashboard";
import { formatKrw, formatKstDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * 전략 오버뷰 (docs/spec2.md §7.1 — 인스턴스 미선택 시 fallback,
 *               docs/04-api-spec.md §4.1).
 *
 * Phase 1에서는 인스턴스 단위 dashboard 진입 카드까지만 표시한다.
 * Phase 2에서 카드 클릭 → 인스턴스 대시보드 라우팅을 붙인다.
 */
export default function StrategyOverviewSection() {
  const { data, isLoading, error } = useStrategyOverview();

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-muted-foreground">
        <Loader2 className="size-4 animate-spin" />
        전략 오버뷰 불러오는 중...
      </div>
    );
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>전략 오버뷰</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center gap-2 text-sm text-destructive">
          <AlertTriangle className="size-4" />
          {error.message}
        </CardContent>
      </Card>
    );
  }

  const cards = data ?? [];

  if (cards.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>전략 오버뷰</CardTitle>
          <CardDescription>등록된 활성 전략 인스턴스가 없습니다.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <section aria-labelledby="strategy-overview-heading" className="flex flex-col gap-3">
      <h2 id="strategy-overview-heading" className="text-base font-semibold">
        전략 인스턴스 ({cards.length})
      </h2>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => {
          const isAutoPaused = !!card.autoPausedReason;
          return (
            <Card key={card.strategyInstanceId}>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span className="truncate">{card.name}</span>
                  <Badge variant="secondary">
                    {card.executionMode.toUpperCase()}
                  </Badge>
                  {isAutoPaused && (
                    <Badge variant="destructive">AUTO-PAUSED</Badge>
                  )}
                </CardTitle>
                <CardDescription className="flex items-center gap-2">
                  <span>상태: {card.lifecycleState}</span>
                  <span aria-hidden>·</span>
                  <span>감시 종목 {card.watchlistCount}</span>
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-2 text-sm">
                <DashboardKpi label="총 자산" value={formatKrw(card.totalAssetAmount)} />
                <DashboardKpi label="현금" value={formatKrw(card.cashAmount)} />
                <DashboardKpi
                  label="오늘 실현손익"
                  value={formatKrw(card.todayRealizedPnl)}
                  emphasize={
                    card.todayRealizedPnl > 0
                      ? "profit"
                      : card.todayRealizedPnl < 0
                        ? "loss"
                        : undefined
                  }
                />
                <DashboardKpi
                  label="최근 판단"
                  value={
                    card.latestDecisionStatus
                      ? `${card.latestDecisionStatus} · ${formatKstDateTime(card.latestDecisionAt)}`
                      : "없음"
                  }
                />
                {isAutoPaused && card.autoPausedReason && (
                  <p className="text-xs text-destructive">
                    auto_paused: {card.autoPausedReason}
                  </p>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>
    </section>
  );
}

function DashboardKpi({
  label,
  value,
  emphasize,
}: {
  label: string;
  value: string;
  emphasize?: "profit" | "loss";
}) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span
        className={cn(
          "font-medium tabular-nums",
          emphasize === "profit" && "text-profit",
          emphasize === "loss" && "text-loss",
        )}
      >
        {value}
      </span>
    </div>
  );
}
