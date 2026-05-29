import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Eye,
  History,
  Loader2,
  RefreshCcw,
  TrendingUp,
} from "lucide-react";
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
import PaperEvalSeriesChart from "@/components/dashboard/PaperEvalSeriesChart";
import {
  usePaperEvalRecentMatches,
  usePaperEvalSeries,
  usePaperEvalSnapshot,
} from "@/hooks/use-paper-eval";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";
import {
  formatKstDateTime,
  formatRatioAsPercent,
  formatRatioAsSignedPercent,
  toFiniteNumber,
} from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  PaperEvalDailyPoint,
  PaperEvalMetricSnapshot,
} from "@/lib/api-types";

/**
 * 박스 단타 v1 paper 평가 화면 (F1).
 *
 * - 상단 4 카드: 누적 EV (= 평균 net_pnl_pct), hit rate, 누적 net PnL (시계열 합), 비용 wall
 * - 가운데 차트: 일별 net_pnl_pct 누적 추이 (area)
 * - 하단 표: 직전 N 건 paper_trade_match (read-only)
 *
 * 백엔드: PaperEvalController
 * - GET /api/admin/paper-eval/{id}?lookback=N
 * - GET /api/admin/paper-eval/{id}/series?days=D
 * - GET /api/admin/paper-eval/{id}/recent-matches?limit=N
 */

const LOOKBACK_PRESETS = [10, 30, 50, 100] as const;
const SERIES_DAYS_PRESETS = [30, 60, 90] as const;

export default function PaperEvalPage() {
  const params = useParams();
  const instanceId = params.id ?? "";
  const instances = useStrategyInstances();
  const instance = instances.data?.find((row) => row.id === instanceId);

  const [lookback, setLookback] = useState<number>(30);
  const [seriesDays, setSeriesDays] = useState<number>(30);

  const snapshot = usePaperEvalSnapshot(instanceId || null, lookback);
  const series = usePaperEvalSeries(instanceId || null, seriesDays);
  const recent = usePaperEvalRecentMatches(instanceId || null, lookback);

  const isLoading =
    snapshot.isLoading || series.isLoading || recent.isLoading;

  const refetchAll = () => {
    void snapshot.refetch();
    void series.refetch();
    void recent.refetch();
  };

  const cumulativeNetPct = useMemo(
    () => sumNetPnlPct(series.data),
    [series.data],
  );

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title={`paper 평가${instance ? ` · ${instance.name}` : ""}`}
        description="직전 N건의 paper 매매 결과를 기반으로 EV/적중률/누적 PnL/비용 wall(실측 거래비용) 4지표를 표시합니다."
        action={
          <>
            <Button variant="outline" render={<Link to="/settings/instances" />}>
              <ArrowLeft className="size-4" /> 인스턴스 목록
            </Button>
            <Button
              variant="outline"
              render={<Link to={`/settings/instances/${instanceId}/watchlist`} />}
            >
              <Eye className="size-4" /> 감시 종목
            </Button>
            <Button
              variant="outline"
              render={
                <Link
                  to={`/settings/instances/${instanceId}/prompt-versions`}
                />
              }
            >
              <History className="size-4" /> 프롬프트 버전
            </Button>
            <Button
              variant="outline"
              onClick={refetchAll}
              disabled={isLoading}
            >
              <RefreshCcw
                className={cn(
                  "size-4",
                  isLoading ? "animate-spin" : undefined,
                )}
              />
              새로고침
            </Button>
          </>
        }
      />

      {instances.error ? (
        <p className="text-sm text-destructive">{instances.error.message}</p>
      ) : null}

      <Card className="py-0">
        <CardHeader>
          <CardTitle className="text-base">조회 범위</CardTitle>
          <CardDescription>
            상단 4지표/하단 표는 lookback (직전 매매 건수) 기준, 차트는 일자 범위
            기준입니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 pb-4 sm:flex-row sm:items-end sm:justify-between">
          <PresetSwitcher
            label="lookback (직전 매매 N건)"
            value={lookback}
            options={LOOKBACK_PRESETS}
            onChange={setLookback}
            suffix="건"
          />
          <PresetSwitcher
            label="차트 일자 범위"
            value={seriesDays}
            options={SERIES_DAYS_PRESETS}
            onChange={setSeriesDays}
            suffix="일"
          />
        </CardContent>
      </Card>

      {snapshot.error ? (
        <p className="text-sm text-destructive">{snapshot.error.message}</p>
      ) : null}

      <section
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
        aria-label="paper-eval 4지표"
      >
        <MetricCard
          title="누적 EV (평균 net PnL)"
          tone="primary"
          value={
            snapshot.data
              ? formatRatioAsSignedPercent(snapshot.data.ev, 4)
              : "—"
          }
          hint={
            snapshot.data
              ? `직전 ${snapshot.data.tradesCount}건 평균 (운영 기준: ≥ +0.15%)`
              : "매매 데이터 없음"
          }
          loading={snapshot.isLoading}
        />
        <MetricCard
          title="hit rate (승률)"
          value={
            snapshot.data
              ? formatRatioAsPercent(snapshot.data.hitRate)
              : "—"
          }
          hint={
            snapshot.data
              ? `wins ${snapshot.data.wins} / losses ${snapshot.data.losses}`
              : "—"
          }
          loading={snapshot.isLoading}
        />
        <MetricCard
          title={`누적 net PnL (${seriesDays}일)`}
          tone="primary"
          value={
            series.data ? formatSignedPercentNumber(cumulativeNetPct) : "—"
          }
          hint={
            series.data && series.data.length > 0
              ? `${series.data.length}일 기록 합산`
              : "기간 내 매매 없음"
          }
          loading={series.isLoading}
        />
        <MetricCard
          title="비용 wall (평균)"
          value={
            snapshot.data
              ? formatRatioAsSignedPercent(
                  snapshot.data.avgCostTotalPct,
                  4,
                )
              : "—"
          }
          hint="|slip_buy|+|slip_sell|+sell_tax+fee 평균"
          loading={snapshot.isLoading}
        />
      </section>

      {snapshot.data ? <CostBreakdownCard snapshot={snapshot.data} /> : null}

      <Card>
        <CardHeader className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <TrendingUp className="size-4 text-muted-foreground" />
            <CardTitle className="text-base">누적 net PnL 추이</CardTitle>
          </div>
          <CardDescription>
            일별 net_pnl_pct 를 KST 기준으로 합산해 누적한 추이입니다. 매매 없는
            일자는 표시되지 않습니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {series.isLoading ? (
            <div className="flex h-[280px] items-center justify-center text-sm text-muted-foreground">
              <Loader2 className="mr-2 size-4 animate-spin" /> 시계열 불러오는 중…
            </div>
          ) : series.error ? (
            <p className="text-sm text-destructive">{series.error.message}</p>
          ) : !series.data || series.data.length === 0 ? (
            <p className="py-12 text-center text-sm text-muted-foreground">
              {seriesDays}일 내 매매 기록이 없습니다.
            </p>
          ) : (
            <PaperEvalSeriesChart points={series.data} mode="cumulative" />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-col gap-1">
          <CardTitle className="text-base">
            최근 매매 ({recent.data?.length ?? 0}건 / lookback {lookback}건)
          </CardTitle>
          <CardDescription>
            paper_trade_match FIFO 매칭 row. exit_time 내림차순.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {recent.isLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> 매매 이력 불러오는 중…
            </div>
          ) : recent.error ? (
            <p className="text-sm text-destructive">{recent.error.message}</p>
          ) : !recent.data || recent.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              매매 기록이 없습니다.
            </p>
          ) : (
            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>종목</TableHead>
                    <TableHead>진입</TableHead>
                    <TableHead>청산</TableHead>
                    <TableHead className="text-right">보유(분)</TableHead>
                    <TableHead className="text-right">수량</TableHead>
                    <TableHead className="text-right">표면 PnL</TableHead>
                    <TableHead className="text-right">net PnL</TableHead>
                    <TableHead className="text-right">slip(매수)</TableHead>
                    <TableHead className="text-right">slip(매도)</TableHead>
                    <TableHead className="text-right">매도세</TableHead>
                    <TableHead className="text-right">수수료</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {recent.data.map((row) => {
                    const net = toFiniteNumber(row.netPnlPct) ?? 0;
                    return (
                      <TableRow key={row.id}>
                        <TableCell className="font-mono text-xs">
                          {row.symbolCode}
                        </TableCell>
                        <TableCell className="text-xs">
                          {formatKstDateTime(row.entryTime)}
                        </TableCell>
                        <TableCell className="text-xs">
                          {formatKstDateTime(row.exitTime)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {row.holdingMinutes}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatQuantity(row.matchedQuantity)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatRatioAsSignedPercent(row.grossPnlPct, 2)}
                        </TableCell>
                        <TableCell className="text-right">
                          <PnlBadge value={net} />
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
                          {formatRatioAsSignedPercent(row.slippageBuyPct, 4)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
                          {formatRatioAsSignedPercent(row.slippageSellPct, 4)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
                          {formatRatioAsSignedPercent(row.sellTaxPct, 4)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
                          {formatRatioAsSignedPercent(row.feePct, 4)}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function PresetSwitcher<T extends number>({
  label,
  value,
  options,
  onChange,
  suffix,
}: {
  label: string;
  value: T;
  options: readonly T[];
  onChange: (next: T) => void;
  suffix?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <div className="flex flex-wrap gap-1.5">
        {options.map((opt) => (
          <Button
            key={opt}
            type="button"
            size="sm"
            variant={opt === value ? "default" : "outline"}
            onClick={() => onChange(opt)}
          >
            {opt}
            {suffix ?? ""}
          </Button>
        ))}
      </div>
    </div>
  );
}

function MetricCard({
  title,
  value,
  hint,
  tone = "default",
  loading,
}: {
  title: string;
  value: string;
  hint?: string;
  tone?: "default" | "primary";
  loading?: boolean;
}) {
  return (
    <Card className={cn(tone === "primary" ? "ring-1 ring-primary/20" : "")}>
      <CardHeader className="pb-1">
        <CardTitle className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-1">
        <span className="text-2xl font-semibold tabular-nums">
          {loading ? (
            <Loader2 className="inline size-5 animate-spin text-muted-foreground" />
          ) : (
            value
          )}
        </span>
        {hint ? (
          <span className="text-xs text-muted-foreground">{hint}</span>
        ) : null}
      </CardContent>
    </Card>
  );
}

function CostBreakdownCard({ snapshot }: { snapshot: PaperEvalMetricSnapshot }) {
  return (
    <Card>
      <CardHeader className="flex flex-col gap-1">
        <CardTitle className="text-base">비용 breakdown (직전 N건 평균)</CardTitle>
        <CardDescription>
          slippage 는 signed (음수면 운 좋게 더 좋은 가격), 세금/수수료는 항상 양수.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
          <CostItem
            label="매수 슬리피지(signed)"
            value={formatRatioAsSignedPercent(snapshot.avgSlippageBuyPct, 4)}
          />
          <CostItem
            label="매도 슬리피지(signed)"
            value={formatRatioAsSignedPercent(snapshot.avgSlippageSellPct, 4)}
          />
          <CostItem
            label="매도세"
            value={formatRatioAsSignedPercent(snapshot.avgSellTaxPct, 4)}
          />
          <CostItem
            label="위탁수수료"
            value={formatRatioAsSignedPercent(snapshot.avgFeePct, 4)}
          />
          <CostItem
            label="Profit Factor"
            value={
              snapshot.pf === null
                ? "∞ (손실 없음)"
                : (toFiniteNumber(snapshot.pf)?.toFixed(2) ?? "-")
            }
          />
          <CostItem
            label="MDD"
            value={formatRatioAsSignedPercent(snapshot.mdd, 4)}
          />
          <CostItem
            label="누적 이익 합(net)"
            value={formatRatioAsSignedPercent(snapshot.sumProfitPct, 4)}
          />
          <CostItem
            label="누적 손실 합(net)"
            value={formatRatioAsSignedPercent(snapshot.sumLossPct, 4)}
          />
        </dl>
      </CardContent>
    </Card>
  );
}

function CostItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium tabular-nums">{value}</dd>
    </div>
  );
}

function PnlBadge({ value }: { value: number }) {
  const tone =
    value > 0 ? "profit" : value < 0 ? "loss" : "neutral";
  const text =
    value === 0 ? "0.00%" : `${value > 0 ? "+" : ""}${(value * 100).toFixed(2)}%`;
  return (
    <Badge
      variant="outline"
      className={cn(
        "tabular-nums",
        tone === "profit"
          ? "border-[var(--profit)]/40 bg-[var(--profit)]/10 text-[var(--profit)]"
          : tone === "loss"
            ? "border-[var(--loss)]/40 bg-[var(--loss)]/10 text-[var(--loss)]"
            : undefined,
      )}
    >
      {text}
    </Badge>
  );
}

function sumNetPnlPct(points: PaperEvalDailyPoint[] | undefined): number {
  if (!points || points.length === 0) {
    return 0;
  }
  let sum = 0;
  for (const p of points) {
    sum += toFiniteNumber(p.netPnlPct) ?? 0;
  }
  return sum;
}

function formatSignedPercentNumber(value: number): string {
  // value 는 ratio (예: 0.0123). % 단위로 변환 후 부호 포함 2자리.
  const percent = value * 100;
  if (!Number.isFinite(percent)) {
    return "-";
  }
  const sign = percent > 0 ? "+" : "";
  return `${sign}${percent.toFixed(2)}%`;
}

function formatQuantity(value: unknown): string {
  const num = toFiniteNumber(value);
  if (num === null) {
    return "-";
  }
  // 정수면 정수로, 아니면 소수 4자리 (자산 종류 일관)
  if (Number.isInteger(num)) {
    return new Intl.NumberFormat("ko-KR").format(num);
  }
  return new Intl.NumberFormat("ko-KR", {
    maximumFractionDigits: 4,
  }).format(num);
}
