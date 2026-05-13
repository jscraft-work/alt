import {
  useState,
  type FormEvent,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { Loader2, RefreshCw } from "lucide-react";
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";
import { useSelectableStrategyInstances } from "@/hooks/use-strategy-instances";
import {
  useTradeDecisionDetail,
  useTradeDecisions,
  useTradeOrderDetail,
  useTradeOrders,
} from "@/hooks/use-trades";
import type {
  ApiPagedMeta,
  CycleStatus,
  OrderIntentView,
  OrderRefView,
  TradeDecisionDetail,
  TradeOrderDetail,
  TradeOrderStatus,
  TradeSide,
} from "@/lib/api-types";
import {
  formatKrw,
  formatKstDateTime,
  formatNumber,
  formatPercent,
} from "@/lib/format";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 20;
const KST_TIME_ZONE = "Asia/Seoul";

const ORDER_STATUS_OPTIONS = [
  { value: "", label: "전체 상태" },
  { value: "requested", label: "requested" },
  { value: "accepted", label: "accepted" },
  { value: "partial", label: "partial" },
  { value: "filled", label: "filled" },
  { value: "canceled", label: "canceled" },
  { value: "rejected", label: "rejected" },
  { value: "failed", label: "failed" },
] as const;

const DECISION_STATUS_OPTIONS = [
  { value: "", label: "전체 상태" },
  { value: "EXECUTE", label: "EXECUTE" },
  { value: "HOLD", label: "HOLD" },
  { value: "FAILED", label: "FAILED" },
] as const;

type TradesTab = "orders" | "decisions";

interface OrderFilters {
  symbolCode: string;
  orderStatus: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  size: number;
}

interface DecisionFilters {
  cycleStatus: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  size: number;
}

export default function TradesPage() {
  const { selectedInstanceId, setSelectedInstanceId } =
    useStrategyInstanceSelection();
  const { data: selectableInstances, isLoading: isInstancesLoading } =
    useSelectableStrategyInstances();

  const [tab, setTab] = useState<TradesTab>("orders");
  const [orderFilters, setOrderFilters] = useState<OrderFilters>(() =>
    createOrderFilters(),
  );
  const [appliedOrderFilters, setAppliedOrderFilters] = useState<OrderFilters>(() =>
    createOrderFilters(),
  );
  const [decisionFilters, setDecisionFilters] = useState<DecisionFilters>(() =>
    createDecisionFilters(),
  );
  const [appliedDecisionFilters, setAppliedDecisionFilters] =
    useState<DecisionFilters>(() => createDecisionFilters());

  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [selectedDecisionId, setSelectedDecisionId] = useState<string | null>(
    null,
  );

  const tradeOrdersQuery = useTradeOrders({
    strategyInstanceId: selectedInstanceId,
    symbolCode: normalizeTextFilter(appliedOrderFilters.symbolCode),
    orderStatus: normalizeTextFilter(appliedOrderFilters.orderStatus),
    dateFrom: appliedOrderFilters.dateFrom,
    dateTo: appliedOrderFilters.dateTo,
    page: appliedOrderFilters.page,
    size: appliedOrderFilters.size,
  });
  const tradeOrderDetailQuery = useTradeOrderDetail(selectedOrderId);

  const tradeDecisionsQuery = useTradeDecisions({
    strategyInstanceId: selectedInstanceId,
    cycleStatus: normalizeTextFilter(appliedDecisionFilters.cycleStatus),
    dateFrom: appliedDecisionFilters.dateFrom,
    dateTo: appliedDecisionFilters.dateTo,
    page: appliedDecisionFilters.page,
    size: appliedDecisionFilters.size,
  });
  const tradeDecisionDetailQuery = useTradeDecisionDetail(selectedDecisionId);

  const activeListQuery =
    tab === "orders" ? tradeOrdersQuery : tradeDecisionsQuery;
  const activeMeta = activeListQuery.data?.meta ?? null;
  const isActiveRefreshing =
    activeListQuery.isFetching && !activeListQuery.isPending;

  const showOrderInstanceColumn = selectedInstanceId === null;
  const showDecisionInstanceColumn = selectedInstanceId === null;

  const onSubmitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (tab === "orders") {
      setAppliedOrderFilters({
        ...orderFilters,
        symbolCode: orderFilters.symbolCode.trim(),
        page: 1,
      });
      setOrderFilters((prev) => ({ ...prev, symbolCode: prev.symbolCode.trim(), page: 1 }));
      return;
    }

    setAppliedDecisionFilters({ ...decisionFilters, page: 1 });
    setDecisionFilters((prev) => ({ ...prev, page: 1 }));
  };

  const onResetFilters = () => {
    if (tab === "orders") {
      const next = createOrderFilters();
      setOrderFilters(next);
      setAppliedOrderFilters(next);
      return;
    }

    const next = createDecisionFilters();
    setDecisionFilters(next);
    setAppliedDecisionFilters(next);
  };

  const movePage = (nextPage: number) => {
    if (!activeMeta || nextPage < 1 || nextPage > activeMeta.totalPages) {
      return;
    }

    if (tab === "orders") {
      setOrderFilters((prev) => ({ ...prev, page: nextPage }));
      setAppliedOrderFilters((prev) => ({ ...prev, page: nextPage }));
      return;
    }

    setDecisionFilters((prev) => ({ ...prev, page: nextPage }));
    setAppliedDecisionFilters((prev) => ({ ...prev, page: nextPage }));
  };

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold">매매이력</h1>
          <p className="text-sm text-muted-foreground">
            주문 결과와 LLM 판단 로그를 최근 7일 기본 범위로 조회합니다.
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            void activeListQuery.refetch();
          }}
          disabled={activeListQuery.isFetching}
        >
          {isActiveRefreshing ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <RefreshCw className="size-4" />
          )}
          새로고침
        </Button>
      </header>

      <div className="flex flex-wrap items-center gap-2">
        <TabButton
          active={tab === "orders"}
          onClick={() => {
            setSelectedOrderId(null);
            setSelectedDecisionId(null);
            setTab("orders");
          }}
          label="주문 이력"
        />
        <TabButton
          active={tab === "decisions"}
          onClick={() => {
            setSelectedOrderId(null);
            setSelectedDecisionId(null);
            setTab("decisions");
          }}
          label="판단 로그"
        />
        <span className="text-xs text-muted-foreground">
          전역 선택 인스턴스는 기본 필터로 자동 반영됩니다.
        </span>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>조회 필터</CardTitle>
          <CardDescription>
            {tab === "orders"
              ? "전략 인스턴스, 기간, 종목 코드, 주문 상태 기준으로 조회합니다."
              : "전략 인스턴스, 기간, 판단 상태 기준으로 조회합니다."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={onSubmitFilters}>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
              <FilterField label="전략 인스턴스" htmlFor="trade-instance">
                <div className="relative">
                  <select
                    id="trade-instance"
                    className={cn(
                      "h-8 w-full rounded-lg border border-input bg-background px-2.5 pr-8 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
                      isInstancesLoading && "text-muted-foreground",
                    )}
                    value={selectedInstanceId ?? ""}
                    onChange={(event) => {
                      setSelectedInstanceId(event.target.value || null);
                    }}
                    disabled={isInstancesLoading}
                  >
                    <option value="">전체 전략</option>
                    {(selectableInstances ?? []).map((instance) => (
                      <option key={instance.id} value={instance.id}>
                        {instance.name}
                      </option>
                    ))}
                  </select>
                  {isInstancesLoading && (
                    <Loader2 className="pointer-events-none absolute top-1/2 right-2 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />
                  )}
                </div>
              </FilterField>

              <FilterField label="시작일" htmlFor="trade-date-from">
                <Input
                  id="trade-date-from"
                  type="date"
                  value={
                    tab === "orders"
                      ? orderFilters.dateFrom
                      : decisionFilters.dateFrom
                  }
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    if (tab === "orders") {
                      setOrderFilters((prev) => ({
                        ...prev,
                        dateFrom: nextValue,
                      }));
                      return;
                    }
                    setDecisionFilters((prev) => ({
                      ...prev,
                      dateFrom: nextValue,
                    }));
                  }}
                />
              </FilterField>

              <FilterField label="종료일" htmlFor="trade-date-to">
                <Input
                  id="trade-date-to"
                  type="date"
                  value={
                    tab === "orders" ? orderFilters.dateTo : decisionFilters.dateTo
                  }
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    if (tab === "orders") {
                      setOrderFilters((prev) => ({
                        ...prev,
                        dateTo: nextValue,
                      }));
                      return;
                    }
                    setDecisionFilters((prev) => ({
                      ...prev,
                      dateTo: nextValue,
                    }));
                  }}
                />
              </FilterField>

              {tab === "orders" ? (
                <FilterField label="종목 코드" htmlFor="trade-symbol-code">
                  <Input
                    id="trade-symbol-code"
                    placeholder="예: 005930"
                    value={orderFilters.symbolCode}
                    onChange={(event) => {
                      setOrderFilters((prev) => ({
                        ...prev,
                        symbolCode: event.target.value,
                      }));
                    }}
                  />
                </FilterField>
              ) : (
                <FilterField label="판단 상태" htmlFor="trade-cycle-status">
                  <select
                    id="trade-cycle-status"
                    className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={decisionFilters.cycleStatus}
                    onChange={(event) => {
                      setDecisionFilters((prev) => ({
                        ...prev,
                        cycleStatus: event.target.value,
                      }));
                    }}
                  >
                    {DECISION_STATUS_OPTIONS.map((option) => (
                      <option key={option.value || "all"} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </FilterField>
              )}

              {tab === "orders" ? (
                <FilterField label="주문 상태" htmlFor="trade-order-status">
                  <select
                    id="trade-order-status"
                    className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={orderFilters.orderStatus}
                    onChange={(event) => {
                      setOrderFilters((prev) => ({
                        ...prev,
                        orderStatus: event.target.value,
                      }));
                    }}
                  >
                    {ORDER_STATUS_OPTIONS.map((option) => (
                      <option key={option.value || "all"} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </FilterField>
              ) : (
                <div className="flex items-end">
                  <p className="text-xs text-muted-foreground">
                    현재 판단 로그 API는 종목 코드 조건을 제공하지 않습니다.
                  </p>
                </div>
              )}
            </div>

            <div className="flex flex-wrap items-center justify-end gap-2">
              <Button type="button" variant="outline" onClick={onResetFilters}>
                기본값 복원
              </Button>
              <Button type="submit">조회</Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {tab === "orders" ? (
        <TradeOrdersTable
          query={tradeOrdersQuery}
          showInstanceColumn={showOrderInstanceColumn}
          onRowClick={setSelectedOrderId}
        />
      ) : (
        <TradeDecisionsTable
          query={tradeDecisionsQuery}
          showInstanceColumn={showDecisionInstanceColumn}
          onRowClick={setSelectedDecisionId}
        />
      )}

      <PaginationBar meta={activeMeta} onMovePage={movePage} />

      <TradeOrderDetailDialog
        open={selectedOrderId !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedOrderId(null);
          }
        }}
        query={tradeOrderDetailQuery}
      />

      <TradeDecisionDetailDialog
        open={selectedDecisionId !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedDecisionId(null);
          }
        }}
        query={tradeDecisionDetailQuery}
      />
    </div>
  );
}

function TradeOrdersTable({
  query,
  showInstanceColumn,
  onRowClick,
}: {
  query: ReturnType<typeof useTradeOrders>;
  showInstanceColumn: boolean;
  onRowClick: (id: string) => void;
}) {
  if (query.isPending && !query.data) {
    return <LoadingCard label="주문 이력을 불러오는 중..." />;
  }

  if (query.error) {
    return <ErrorCard message={query.error.message} onRetry={() => void query.refetch()} />;
  }

  if (!query.data || query.data.data.length === 0) {
    return <EmptyCard message="조회 조건에 맞는 주문 이력이 없습니다." />;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>주문 이력</CardTitle>
        <CardDescription>
          행을 클릭하면 주문 상세와 체결 이후 포트폴리오 스냅샷을 확인할 수
          있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>주문 시각</TableHead>
              {showInstanceColumn && <TableHead>인스턴스</TableHead>}
              <TableHead>종목 코드</TableHead>
              <TableHead>매매</TableHead>
              <TableHead className="text-right">주문가</TableHead>
              <TableHead className="text-right">수량</TableHead>
              <TableHead className="text-right">주문 금액</TableHead>
              <TableHead className="text-right">누적 체결</TableHead>
              <TableHead>상태</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.data.map((row) => (
              <ClickableTableRow
                key={row.id}
                onClick={() => onRowClick(row.id)}
                label={`주문 ${row.id} 상세 열기`}
              >
                <TableCell className="whitespace-nowrap">
                  {formatKstDateTime(row.requestedAt)}
                </TableCell>
                {showInstanceColumn && (
                  <TableCell className="font-medium">{row.instanceName}</TableCell>
                )}
                <TableCell className="font-medium">{row.symbolCode}</TableCell>
                <TableCell>
                  <TradeSideBadge side={row.side} />
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatKrw(row.requestedPrice)}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatNumber(row.requestedQuantity)}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatOrderAmount(row.requestedPrice, row.requestedQuantity)}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatNumber(row.filledQuantity)}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <TradeOrderStatusBadge status={row.orderStatus} />
                    <Badge variant="outline">{row.executionMode.toUpperCase()}</Badge>
                  </div>
                </TableCell>
              </ClickableTableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function TradeDecisionsTable({
  query,
  showInstanceColumn,
  onRowClick,
}: {
  query: ReturnType<typeof useTradeDecisions>;
  showInstanceColumn: boolean;
  onRowClick: (id: string) => void;
}) {
  if (query.isPending && !query.data) {
    return <LoadingCard label="판단 로그를 불러오는 중..." />;
  }

  if (query.error) {
    return <ErrorCard message={query.error.message} onRetry={() => void query.refetch()} />;
  }

  if (!query.data || query.data.data.length === 0) {
    return <EmptyCard message="조회 조건에 맞는 판단 로그가 없습니다." />;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>판단 로그</CardTitle>
        <CardDescription>
          행을 클릭하면 LLM 요청/응답 원문과 파싱된 판단 JSON, 연결 주문을 확인할
          수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>판단 시각</TableHead>
              {showInstanceColumn && <TableHead>인스턴스</TableHead>}
              <TableHead>상태</TableHead>
              <TableHead>요약</TableHead>
              <TableHead className="text-right">신뢰도</TableHead>
              <TableHead className="text-right">연결 주문</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.data.map((row) => (
              <ClickableTableRow
                key={row.id}
                onClick={() => onRowClick(row.id)}
                label={`판단 로그 ${row.id} 상세 열기`}
              >
                <TableCell className="whitespace-nowrap">
                  {formatKstDateTime(row.cycleStartedAt)}
                </TableCell>
                {showInstanceColumn && (
                  <TableCell className="font-medium">{row.instanceName}</TableCell>
                )}
                <TableCell>
                  <TradeDecisionStatusBadge status={row.cycleStatus} />
                </TableCell>
                <TableCell className="max-w-[420px]">
                  <div className="flex flex-col gap-1">
                    <span className="font-medium">
                      {row.summary || "요약 없음"}
                    </span>
                    {row.failureReason && (
                      <span className="text-xs text-destructive">
                        실패 사유: {row.failureReason}
                      </span>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatConfidence(row.confidence)}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatNumber(row.orderCount)}
                </TableCell>
              </ClickableTableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function TradeOrderDetailDialog({
  open,
  onOpenChange,
  query,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  query: ReturnType<typeof useTradeOrderDetail>;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[88vh] max-w-4xl overflow-hidden p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>주문 상세</DialogTitle>
          <DialogDescription>
            주문 상태 변화와 체결 이후 포트폴리오 스냅샷을 조회합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="overflow-y-auto px-6 py-5">
          {query.isPending && <LoadingInline label="주문 상세를 불러오는 중..." />}
          {query.error && (
            <InlineError message={query.error.message} onRetry={() => void query.refetch()} />
          )}
          {query.data && <TradeOrderDetailContent detail={query.data} />}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function TradeDecisionDetailDialog({
  open,
  onOpenChange,
  query,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  query: ReturnType<typeof useTradeDecisionDetail>;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-6xl overflow-hidden p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>판단 로그 상세</DialogTitle>
          <DialogDescription>
            요청/응답 원문, 파싱 결과, 설정 스냅샷, 연결 주문을 함께 조회합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="overflow-y-auto px-6 py-5">
          {query.isPending && <LoadingInline label="판단 로그 상세를 불러오는 중..." />}
          {query.error && (
            <InlineError message={query.error.message} onRetry={() => void query.refetch()} />
          )}
          {query.data && <TradeDecisionDetailContent detail={query.data} />}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function TradeOrderDetailContent({ detail }: { detail: TradeOrderDetail }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <DetailField label="주문 ID" value={detail.id} />
        <DetailField label="인스턴스 ID" value={detail.strategyInstanceId} />
        <DetailField label="주문 의도 ID" value={detail.tradeOrderIntentId} />
        <DetailField label="클라이언트 주문 ID" value={detail.clientOrderId} />
        <DetailField label="브로커 주문번호" value={detail.brokerOrderNo} />
        <DetailField label="주문 상태" value={<TradeOrderStatusBadge status={detail.orderStatus} />} />
        <DetailField label="실행 모드" value={detail.executionMode.toUpperCase()} />
        <DetailField label="실패 사유" value={detail.failureReason} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <Card size="sm">
          <CardHeader>
            <CardTitle>주문 수치</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
            <DetailField label="주문 수량" value={formatNumber(detail.requestedQuantity)} />
            <DetailField label="주문가" value={formatKrw(detail.requestedPrice)} />
            <DetailField label="주문 금액" value={formatOrderAmount(detail.requestedPrice, detail.requestedQuantity)} />
            <DetailField label="체결 수량" value={formatNumber(detail.filledQuantity)} />
            <DetailField label="평균 체결가" value={formatKrw(detail.avgFilledPrice)} />
          </CardContent>
        </Card>

        <Card size="sm">
          <CardHeader>
            <CardTitle>시각 정보</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <DetailField label="요청 시각" value={formatKstDateTime(detail.requestedAt)} />
            <DetailField label="접수 시각" value={formatKstDateTime(detail.acceptedAt)} />
            <DetailField label="최종 체결 시각" value={formatKstDateTime(detail.filledAt)} />
          </CardContent>
        </Card>
      </div>

      <Card size="sm">
        <CardHeader>
          <CardTitle>체결 이후 포트폴리오</CardTitle>
          <CardDescription>
            백엔드가 저장한 주문 직후 스냅샷 원문입니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <JsonPreview value={detail.portfolioAfter} />
        </CardContent>
      </Card>
    </div>
  );
}

function TradeDecisionDetailContent({
  detail,
}: {
  detail: TradeDecisionDetail;
}) {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <DetailField label="판단 로그 ID" value={detail.id} />
        <DetailField label="인스턴스" value={detail.instanceName} />
        <DetailField
          label="판단 상태"
          value={<TradeDecisionStatusBadge status={detail.cycleStatus} />}
        />
        <DetailField label="요약" value={detail.summary} />
        <DetailField label="신뢰도" value={formatConfidence(detail.confidence)} />
        <DetailField label="호출 상태" value={detail.callStatus} />
        <DetailField label="엔진" value={detail.engineName} />
        <DetailField label="모델" value={detail.modelName} />
        <DetailField label="실패 사유" value={detail.failureReason} />
        <DetailField label="실패 상세" value={detail.failureDetail} />
        <DetailField label="시작 시각" value={formatKstDateTime(detail.cycleStartedAt)} />
        <DetailField label="종료 시각" value={formatKstDateTime(detail.cycleFinishedAt)} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <Card size="sm">
          <CardHeader>
            <CardTitle>토큰 및 비용</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <DetailField label="입력 토큰" value={formatNumber(detail.inputTokens)} />
            <DetailField label="출력 토큰" value={formatNumber(detail.outputTokens)} />
            <DetailField label="추정 비용" value={formatEstimatedCost(detail.estimatedCost)} />
          </CardContent>
        </Card>

        <Card size="sm" className="xl:col-span-2">
          <CardHeader>
            <CardTitle>파싱된 판단 결과</CardTitle>
          </CardHeader>
          <CardContent>
            <JsonPreview value={detail.parsedDecision} />
          </CardContent>
        </Card>
      </div>

      <Card size="sm">
        <CardHeader>
          <CardTitle>주문 의도</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          {detail.orderIntents.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              연결된 주문 의도가 없습니다.
            </p>
          ) : (
            <OrderIntentTable rows={detail.orderIntents} />
          )}
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle>연결 주문</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          {detail.orders.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              연결된 실제 주문이 없습니다.
            </p>
          ) : (
            <OrderReferenceTable rows={detail.orders} />
          )}
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <Card size="sm">
          <CardHeader>
            <CardTitle>LLM 요청 원문</CardTitle>
          </CardHeader>
          <CardContent>
            <TextPreview value={detail.requestText} />
          </CardContent>
        </Card>

        <Card size="sm">
          <CardHeader>
            <CardTitle>LLM 응답 원문</CardTitle>
          </CardHeader>
          <CardContent>
            <TextPreview value={detail.responseText} />
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <Card size="sm">
          <CardHeader>
            <CardTitle>stdout</CardTitle>
          </CardHeader>
          <CardContent>
            <TextPreview value={detail.stdoutText} />
          </CardContent>
        </Card>

        <Card size="sm">
          <CardHeader>
            <CardTitle>stderr</CardTitle>
          </CardHeader>
          <CardContent>
            <TextPreview value={detail.stderrText} />
          </CardContent>
        </Card>
      </div>

      <Card size="sm">
        <CardHeader>
          <CardTitle>판단 시점 설정 스냅샷</CardTitle>
        </CardHeader>
        <CardContent>
          <JsonPreview value={detail.settingsSnapshot} />
        </CardContent>
      </Card>
    </div>
  );
}

function OrderIntentTable({ rows }: { rows: OrderIntentView[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>순번</TableHead>
          <TableHead>종목 코드</TableHead>
          <TableHead>매매</TableHead>
          <TableHead className="text-right">수량</TableHead>
          <TableHead>주문 유형</TableHead>
          <TableHead className="text-right">가격</TableHead>
          <TableHead>차단 사유</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.id}>
            <TableCell>{row.sequenceNo}</TableCell>
            <TableCell className="font-medium">{row.symbolCode}</TableCell>
            <TableCell>
              <TradeSideBadge side={row.side} />
            </TableCell>
            <TableCell className="text-right tabular-nums">
              {formatNumber(row.quantity)}
            </TableCell>
            <TableCell>{row.orderType}</TableCell>
            <TableCell className="text-right tabular-nums">
              {formatKrw(row.price)}
            </TableCell>
            <TableCell>
              <div className="flex flex-col gap-2">
                <span>{row.executionBlockedReason ?? "-"}</span>
                {row.rationale && (
                  <span className="text-xs text-muted-foreground">
                    근거: {row.rationale}
                  </span>
                )}
                <JsonPreview value={row.evidence} compact />
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function OrderReferenceTable({ rows }: { rows: OrderRefView[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>주문 ID</TableHead>
          <TableHead>주문 의도 ID</TableHead>
          <TableHead>상태</TableHead>
          <TableHead>요청 시각</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.id}>
            <TableCell className="font-medium">{row.id}</TableCell>
            <TableCell>{row.tradeOrderIntentId}</TableCell>
            <TableCell>
              <TradeOrderStatusBadge status={row.orderStatus} />
            </TableCell>
            <TableCell>{formatKstDateTime(row.requestedAt)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function PaginationBar({
  meta,
  onMovePage,
}: {
  meta: ApiPagedMeta | null;
  onMovePage: (page: number) => void;
}) {
  if (!meta) {
    return null;
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border bg-card px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <div className="text-muted-foreground">
        총 {formatNumber(meta.totalElements)}건, {meta.page} / {meta.totalPages} 페이지
      </div>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          onClick={() => onMovePage(meta.page - 1)}
          disabled={meta.page <= 1}
        >
          이전
        </Button>
        <Button
          variant="outline"
          onClick={() => onMovePage(meta.page + 1)}
          disabled={meta.page >= meta.totalPages}
        >
          다음
        </Button>
      </div>
    </div>
  );
}

function ClickableTableRow({
  children,
  onClick,
  label,
}: {
  children: ReactNode;
  onClick: () => void;
  label: string;
}) {
  const onKeyDown = (event: KeyboardEvent<HTMLTableRowElement>) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onClick();
    }
  };

  return (
    <TableRow
      role="button"
      tabIndex={0}
      aria-label={label}
      className="cursor-pointer"
      onClick={onClick}
      onKeyDown={onKeyDown}
    >
      {children}
    </TableRow>
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
    <div className="flex flex-col gap-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}

function DetailField({
  label,
  value,
}: {
  label: string;
  value: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border bg-muted/20 p-3 text-sm">
      <span className="text-xs text-muted-foreground">{label}</span>
      <div className="break-all font-medium">
        {value === null || value === undefined || value === "" ? "-" : value}
      </div>
    </div>
  );
}

function JsonPreview({
  value,
  compact = false,
}: {
  value: unknown;
  compact?: boolean;
}) {
  if (value === null || value === undefined) {
    return <p className="text-sm text-muted-foreground">-</p>;
  }

  let text: string;
  try {
    text = JSON.stringify(value, null, compact ? 0 : 2);
  } catch {
    text = String(value);
  }

  return (
    <pre
      className={cn(
        "overflow-x-auto rounded-lg bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap break-all",
        compact && "max-h-24",
      )}
    >
      {text}
    </pre>
  );
}

function TextPreview({ value }: { value: string | null | undefined }) {
  if (!value) {
    return <p className="text-sm text-muted-foreground">-</p>;
  }

  return (
    <pre className="max-h-80 overflow-auto rounded-lg bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap break-all">
      {value}
    </pre>
  );
}

function LoadingCard({ label }: { label: string }) {
  return (
    <Card>
      <CardContent className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" />
        {label}
      </CardContent>
    </Card>
  );
}

function EmptyCard({ message }: { message: string }) {
  return (
    <Card>
      <CardContent className="py-8 text-sm text-muted-foreground">
        {message}
      </CardContent>
    </Card>
  );
}

function ErrorCard({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 py-8 text-sm">
        <p className="text-destructive">{message}</p>
        <div>
          <Button variant="outline" onClick={onRetry}>
            다시 시도
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function LoadingInline({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
      <Loader2 className="size-4 animate-spin" />
      {label}
    </div>
  );
}

function InlineError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="flex flex-col gap-3 py-4 text-sm">
      <p className="text-destructive">{message}</p>
      <div>
        <Button variant="outline" onClick={onRetry}>
          다시 시도
        </Button>
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  label,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <Button variant={active ? "default" : "outline"} onClick={onClick}>
      {label}
    </Button>
  );
}

function TradeSideBadge({ side }: { side: TradeSide }) {
  return (
    <Badge variant={side === "SELL" ? "destructive" : "default"}>{side}</Badge>
  );
}

function TradeOrderStatusBadge({ status }: { status: TradeOrderStatus }) {
  const variant: "default" | "destructive" | "outline" =
    status === "failed" || status === "rejected"
      ? "destructive"
      : status === "filled"
        ? "default"
        : "outline";

  return <Badge variant={variant}>{status}</Badge>;
}

function TradeDecisionStatusBadge({ status }: { status: CycleStatus }) {
  const variant: "default" | "destructive" | "outline" =
    status === "FAILED"
      ? "destructive"
      : status === "EXECUTE"
        ? "default"
        : "outline";

  return <Badge variant={variant}>{status}</Badge>;
}

function formatOrderAmount(
  price: number | null | undefined,
  quantity: number | null | undefined,
) {
  if (price === null || price === undefined || quantity === null || quantity === undefined) {
    return "-";
  }

  return formatKrw(price * quantity);
}

function formatConfidence(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "-";
  }

  return formatPercent(value * 100);
}

function formatEstimatedCost(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "-";
  }

  return value.toLocaleString("ko-KR", {
    minimumFractionDigits: 4,
    maximumFractionDigits: 6,
  });
}

function normalizeTextFilter(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function createOrderFilters(): OrderFilters {
  const range = getDefaultDateRange();

  return {
    symbolCode: "",
    orderStatus: "",
    dateFrom: range.dateFrom,
    dateTo: range.dateTo,
    page: 1,
    size: PAGE_SIZE,
  };
}

function createDecisionFilters(): DecisionFilters {
  const range = getDefaultDateRange();

  return {
    cycleStatus: "",
    dateFrom: range.dateFrom,
    dateTo: range.dateTo,
    page: 1,
    size: PAGE_SIZE,
  };
}

function getDefaultDateRange() {
  const kstToday = getKstCalendarDate(new Date());
  const endDate = new Date(Date.UTC(kstToday.year, kstToday.month - 1, kstToday.day));
  const startDate = new Date(endDate);
  startDate.setUTCDate(startDate.getUTCDate() - 6);

  return {
    dateFrom: formatDateOnly(startDate),
    dateTo: formatDateOnly(endDate),
  };
}

function getKstCalendarDate(date: Date) {
  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = formatter.formatToParts(date);

  return {
    year: Number(parts.find((part) => part.type === "year")?.value ?? "0"),
    month: Number(parts.find((part) => part.type === "month")?.value ?? "0"),
    day: Number(parts.find((part) => part.type === "day")?.value ?? "0"),
  };
}

function formatDateOnly(date: Date) {
  const year = String(date.getUTCFullYear());
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
