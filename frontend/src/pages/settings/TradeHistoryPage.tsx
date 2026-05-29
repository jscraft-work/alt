import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  Gauge,
  History,
  Loader2,
  RefreshCcw,
  Search,
  X,
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
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import SettingsHeader from "@/components/settings/SettingsHeader";
import FormField from "@/components/settings/FormField";
import { usePaperTradeHistory } from "@/hooks/use-paper-trade-history";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";
import {
  formatKstDateTime,
  formatRatioAsSignedPercent,
  toFiniteNumber,
} from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  TradeHistoryFilter,
  TradeHistoryRow,
  TradeHistorySortField,
} from "@/lib/api-types";

/**
 * paper_trade_match 페이지네이션/필터 화면 (F3).
 *
 * - 상단: 요약 카드 3개 (총 N건, win rate, 누적 net PnL — 조회 결과 기준)
 * - 필터 영역: 종목 / 날짜 from-to / win|loss toggle / 정렬 dropdown
 * - 표: 11 컬럼 (entry/exit/holding/symbol/qty + V18 gross/net + V17 비용 5 컬럼)
 * - 페이지네이션 (Prev/Next + 페이지 표시)
 */

const PAGE_SIZE = 50;

export default function TradeHistoryPage() {
  const params = useParams();
  const instanceId = params.id ?? "";
  const instances = useStrategyInstances();
  const instance = instances.data?.find((row) => row.id === instanceId);

  const [symbolInput, setSymbolInput] = useState("");
  const [appliedSymbol, setAppliedSymbol] = useState<string | null>(null);
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");
  const [winOnly, setWinOnly] = useState(false);
  const [lossOnly, setLossOnly] = useState(false);
  const [sortField, setSortField] =
    useState<TradeHistorySortField>("exit_time");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [page, setPage] = useState(0);

  const filter: TradeHistoryFilter = useMemo(
    () => ({
      page,
      size: PAGE_SIZE,
      from: from ? toBackendIso(from) : null,
      to: to ? toBackendIso(to) : null,
      symbol: appliedSymbol,
      winOnly,
      lossOnly,
      sort: `${sortField}:${sortDir}`,
    }),
    [page, from, to, appliedSymbol, winOnly, lossOnly, sortField, sortDir],
  );

  const history = usePaperTradeHistory(instanceId || null, filter);

  const sumNetPercent = useMemo(() => {
    const raw = toFiniteNumber(history.data?.summary.sumNetPnlPct) ?? 0;
    return raw * 100;
  }, [history.data?.summary.sumNetPnlPct]);

  const winRate =
    history.data && history.data.summary.tradesCount > 0
      ? history.data.summary.winCount / history.data.summary.tradesCount
      : null;

  const applyFilters = () => {
    setAppliedSymbol(symbolInput.trim() || null);
    setPage(0);
  };

  const resetFilters = () => {
    setSymbolInput("");
    setAppliedSymbol(null);
    setFrom("");
    setTo("");
    setWinOnly(false);
    setLossOnly(false);
    setSortField("exit_time");
    setSortDir("desc");
    setPage(0);
  };

  const toggleSort = (field: TradeHistorySortField) => {
    if (sortField === field) {
      setSortDir((prev) => (prev === "desc" ? "asc" : "desc"));
    } else {
      setSortField(field);
      setSortDir("desc");
    }
    setPage(0);
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title={`매매 이력${instance ? ` · ${instance.name}` : ""}`}
        description="paper_trade_match FIFO 매칭 row 의 페이지네이션/필터 조회. V17 비용 breakdown (slippage / 매도세 / 수수료 / actual) 포함."
        action={
          <>
            <Button variant="outline" render={<Link to="/settings/instances" />}>
              <ArrowLeft className="size-4" /> 인스턴스 목록
            </Button>
            <Button
              variant="outline"
              render={
                <Link to={`/settings/instances/${instanceId}/paper-eval`} />
              }
            >
              <Gauge className="size-4" /> paper 평가
            </Button>
            <Button
              variant="outline"
              render={
                <Link to={`/settings/instances/${instanceId}/watchlist`} />
              }
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
              onClick={() => void history.refetch()}
              disabled={history.isFetching}
            >
              <RefreshCcw
                className={cn(
                  "size-4",
                  history.isFetching ? "animate-spin" : undefined,
                )}
              />
              새로고침
            </Button>
          </>
        }
      />

      {/* 상단 요약 */}
      <section
        className="grid grid-cols-1 gap-3 sm:grid-cols-3"
        aria-label="조회 결과 요약"
      >
        <SummaryCard
          title="총 매매"
          value={
            history.data
              ? `${history.data.summary.tradesCount.toLocaleString("ko-KR")}건`
              : "—"
          }
          loading={history.isLoading}
          hint={
            history.data
              ? `wins ${history.data.summary.winCount} / losses ${history.data.summary.lossCount}`
              : undefined
          }
        />
        <SummaryCard
          title="승률 (win rate)"
          value={
            winRate === null
              ? "—"
              : `${(winRate * 100).toFixed(2)}%`
          }
          loading={history.isLoading}
          hint="net_pnl_pct > 0 기준"
        />
        <SummaryCard
          title="누적 net PnL"
          tone={sumNetPercent > 0 ? "profit" : sumNetPercent < 0 ? "loss" : "default"}
          value={
            history.data
              ? `${sumNetPercent > 0 ? "+" : ""}${sumNetPercent.toFixed(2)}%`
              : "—"
          }
          loading={history.isLoading}
          hint="조회 결과 net_pnl_pct 합 (전체 페이지)"
        />
      </section>

      {/* 필터 영역 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">필터</CardTitle>
          <CardDescription>
            종목, 날짜 범위, win/loss 필터. 박스 단타 v1 단일 셋업 운영 단계이므로
            셋업 필터는 비활성화.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 lg:grid-cols-4">
          <FormField id="th-symbol" label="종목코드">
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="th-symbol"
                value={symbolInput}
                placeholder="005930"
                className="pl-8 pr-10"
                onChange={(e) => setSymbolInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") applyFilters();
                }}
              />
              {symbolInput ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="absolute top-1/2 right-1 -translate-y-1/2"
                  aria-label="종목코드 지우기"
                  onClick={() => setSymbolInput("")}
                >
                  <X className="size-4" />
                </Button>
              ) : null}
            </div>
          </FormField>
          <FormField id="th-from" label="from (exit_time)" helpText="KST 로 입력">
            <Input
              id="th-from"
              type="datetime-local"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </FormField>
          <FormField id="th-to" label="to (exit_time)">
            <Input
              id="th-to"
              type="datetime-local"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </FormField>
          <FormField id="th-flags" label="결과 필터">
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                variant={winOnly ? "default" : "outline"}
                onClick={() => {
                  setWinOnly((v) => !v);
                  if (lossOnly) setLossOnly(false);
                  setPage(0);
                }}
              >
                win only
              </Button>
              <Button
                type="button"
                size="sm"
                variant={lossOnly ? "default" : "outline"}
                onClick={() => {
                  setLossOnly((v) => !v);
                  if (winOnly) setWinOnly(false);
                  setPage(0);
                }}
              >
                loss only
              </Button>
            </div>
          </FormField>

          <div className="flex items-end gap-2 lg:col-span-4">
            <Button onClick={applyFilters}>
              <Search className="size-4" /> 필터 적용
            </Button>
            <Button variant="outline" onClick={resetFilters}>
              초기화
            </Button>
            <div className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
              정렬:
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("exit_time")}
                className={cn(
                  "h-7 gap-1 text-xs",
                  sortField === "exit_time" ? "text-foreground" : undefined,
                )}
              >
                exit_time
                <SortIcon active={sortField === "exit_time"} dir={sortDir} />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("net_pnl_pct")}
                className={cn(
                  "h-7 gap-1 text-xs",
                  sortField === "net_pnl_pct" ? "text-foreground" : undefined,
                )}
              >
                net pnl
                <SortIcon active={sortField === "net_pnl_pct"} dir={sortDir} />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleSort("gross_pnl_pct")}
                className={cn(
                  "h-7 gap-1 text-xs",
                  sortField === "gross_pnl_pct" ? "text-foreground" : undefined,
                )}
              >
                gross pnl
                <SortIcon active={sortField === "gross_pnl_pct"} dir={sortDir} />
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 표 */}
      <Card>
        <CardContent className="px-2 py-3">
          {history.isLoading && !history.data ? (
            <div className="flex items-center gap-2 px-3 py-8 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> 매매 이력 불러오는 중…
            </div>
          ) : history.error ? (
            <p className="px-3 py-4 text-sm text-destructive">
              {history.error.message}
            </p>
          ) : !history.data || history.data.rows.length === 0 ? (
            <p className="px-3 py-8 text-center text-sm text-muted-foreground">
              조회 조건에 매칭되는 매매 이력이 없습니다.
            </p>
          ) : (
            <div className="overflow-x-auto">
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
                    <TableHead className="text-right">매수 amount</TableHead>
                    <TableHead className="text-right">매도 amount</TableHead>
                    <TableHead className="text-right">slippage</TableHead>
                    <TableHead className="text-right">매도세</TableHead>
                    <TableHead className="text-right">수수료</TableHead>
                    <TableHead className="text-right">walk</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {history.data.rows.map((row) => (
                    <TradeRow key={row.matchId} row={row} />
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 페이지네이션 */}
      {history.data && history.data.totalPages > 0 ? (
        <div className="flex items-center justify-between gap-2 px-1 text-sm">
          <span className="text-muted-foreground">
            총 {history.data.totalElements.toLocaleString("ko-KR")} 건 · 페이지{" "}
            {history.data.page + 1} / {history.data.totalPages}
          </span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={history.data.page <= 0 || history.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="size-4" /> 이전
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={
                history.data.page >= history.data.totalPages - 1 ||
                history.isFetching
              }
              onClick={() => setPage((p) => p + 1)}
            >
              다음 <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

// ─────────── helpers / sub components ───────────

function toBackendIso(value: string): string {
  // <input type="datetime-local"> 는 "2026-05-29T13:45" 형태. KST 가정으로 offset 붙임.
  if (!value) return value;
  // 이미 offset 이 있으면 그대로
  if (/[+-]\d{2}:\d{2}$/.test(value) || value.endsWith("Z")) return value;
  return `${value}:00+09:00`;
}

function SummaryCard({
  title,
  value,
  hint,
  loading,
  tone = "default",
}: {
  title: string;
  value: string;
  hint?: string;
  loading?: boolean;
  tone?: "default" | "profit" | "loss";
}) {
  return (
    <Card
      className={cn(
        tone === "profit" ? "ring-1 ring-[var(--profit)]/30" : undefined,
        tone === "loss" ? "ring-1 ring-[var(--loss)]/30" : undefined,
      )}
    >
      <CardHeader className="pb-1">
        <CardTitle className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-1">
          <span
            className={cn(
              "text-2xl font-semibold tabular-nums",
              tone === "profit" ? "text-[var(--profit)]" : undefined,
              tone === "loss" ? "text-[var(--loss)]" : undefined,
            )}
          >
            {loading ? (
              <Loader2 className="inline size-5 animate-spin text-muted-foreground" />
            ) : (
              value
            )}
          </span>
          {hint ? (
            <span className="text-xs text-muted-foreground">{hint}</span>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}

function SortIcon({
  active,
  dir,
}: {
  active: boolean;
  dir: "asc" | "desc";
}) {
  if (!active) {
    return <ArrowUpDown className="size-3" />;
  }
  return dir === "asc" ? (
    <ArrowUp className="size-3" />
  ) : (
    <ArrowDown className="size-3" />
  );
}

function TradeRow({ row }: { row: TradeHistoryRow }) {
  const net = toFiniteNumber(row.netPnlPct) ?? 0;
  // signed slippage 표시: |slip_buy_pct| + |slip_sell_pct| (총합)
  const slipBuy = toFiniteNumber(row.slippageBuyPct);
  const slipSell = toFiniteNumber(row.slippageSellPct);
  const slipText =
    slipBuy === null && slipSell === null
      ? "-"
      : `${formatSignedTrim(slipBuy)} / ${formatSignedTrim(slipSell)}`;
  return (
    <TableRow>
      <TableCell className="font-mono text-xs">{row.symbolCode}</TableCell>
      <TableCell className="text-xs">{formatKstDateTime(row.entryTime)}</TableCell>
      <TableCell className="text-xs">{formatKstDateTime(row.exitTime)}</TableCell>
      <TableCell className="text-right tabular-nums">
        {row.holdingMinutes}
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {formatQty(row.matchedQuantity)}
      </TableCell>
      <TableCell className="text-right tabular-nums text-xs">
        {formatRatioAsSignedPercent(row.grossPnlPct, 2)}
      </TableCell>
      <TableCell className="text-right">
        <PnlBadge value={net} />
      </TableCell>
      <TableCell className="text-right tabular-nums text-xs">
        {formatKrwSlim(row.buyActualAmount)}
      </TableCell>
      <TableCell className="text-right tabular-nums text-xs">
        {formatKrwSlim(row.sellActualAmount)}
      </TableCell>
      <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
        {slipText}
      </TableCell>
      <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
        {formatKrwSlim(row.sellSellTaxAmount)}
      </TableCell>
      <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
        {formatKrwSlim(
          (toFiniteNumber(row.buyCommissionAmount) ?? 0) +
            (toFiniteNumber(row.sellCommissionAmount) ?? 0),
        )}
      </TableCell>
      <TableCell className="text-right tabular-nums text-[11px] text-muted-foreground">
        b{row.buyWalkLevels ?? "-"} / s{row.sellWalkLevels ?? "-"}
      </TableCell>
    </TableRow>
  );
}

function PnlBadge({ value }: { value: number }) {
  const tone = value > 0 ? "profit" : value < 0 ? "loss" : "neutral";
  const text =
    value === 0
      ? "0.00%"
      : `${value > 0 ? "+" : ""}${(value * 100).toFixed(2)}%`;
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

function formatQty(value: unknown): string {
  const num = toFiniteNumber(value);
  if (num === null) return "-";
  return Number.isInteger(num)
    ? new Intl.NumberFormat("ko-KR").format(num)
    : new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 4 }).format(num);
}

function formatKrwSlim(value: unknown): string {
  const num = toFiniteNumber(value);
  if (num === null) return "-";
  return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 }).format(
    num,
  );
}

function formatSignedTrim(value: number | null): string {
  if (value === null) return "-";
  const percent = value * 100;
  const sign = percent > 0 ? "+" : "";
  return `${sign}${percent.toFixed(2)}%`;
}
