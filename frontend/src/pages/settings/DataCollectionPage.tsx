import { useState } from "react";
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Database,
  HelpCircle,
  Loader2,
  Newspaper,
  Plus,
  RefreshCcw,
  Trash2,
  Wifi,
} from "lucide-react";
import { toast } from "sonner";
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
import {
  useDataCollectionSummary,
  useOpsEvents,
  useWsSubscribe,
  useWsSubscriptions,
  useWsUnsubscribe,
} from "@/hooks/use-data-collection";
import { formatKstDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  DataCollectionStatus,
  OpsEventService,
  OpsEventView,
  WsSubscriptionRow,
} from "@/lib/api-types";

/**
 * data-collection admin (F2).
 *
 * - 4 섹션 카드: KIS WS / KIS REST 시세 / DART+네이버 / yfinance 매크로
 * - 각 섹션: lastDataAt + 24h 카운트 + latestEvent + status 색상
 * - KIS WS 추가: 종목 코드 입력 → ad-hoc subscribe 큐에 추가 (60s 내 reconcile 반영)
 * - 추가/제거 + 41 한도 경고 + ops-events 4 서비스별 최근 5건 표
 */
export default function DataCollectionPage() {
  const summary = useDataCollectionSummary();
  const subs = useWsSubscriptions();
  const subscribe = useWsSubscribe();
  const unsubscribe = useWsUnsubscribe();

  const [codeInput, setCodeInput] = useState("");
  const [pendingRemove, setPendingRemove] = useState<string | null>(null);

  const refetchAll = () => {
    void summary.refetch();
    void subs.refetch();
  };

  const onAddSymbols = async () => {
    const codes = codeInput
      .split(/[\s,]+/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (codes.length === 0) {
      toast.message("추가할 종목 코드를 입력해 주세요.");
      return;
    }
    try {
      const res = await subscribe.mutateAsync({ symbolCodes: codes });
      toast.success(
        `ad-hoc 구독 큐에 ${res.addedCount}/${res.requestedCount} 개 종목 추가됨. 60초 내 reconcile 반영.`,
      );
      setCodeInput("");
    } catch {
      // handleMutationError 가 toast 띄움
    }
  };

  const onRemove = async (symbolCode: string) => {
    setPendingRemove(symbolCode);
    try {
      const res = await unsubscribe.mutateAsync(symbolCode);
      if (res.removed) {
        toast.success(`${symbolCode} ad-hoc 구독 제거. 60초 내 reconcile 반영.`);
      } else {
        toast.message(
          `${symbolCode} 는 watchlist 자동 구독 종목입니다. ad-hoc 항목만 제거 가능.`,
        );
      }
    } finally {
      setPendingRemove(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="데이터 수집"
        description="KIS WebSocket / KIS REST 시세 / DART+네이버 / yfinance 매크로 4개 채널 통합 모니터링. 30초마다 자동 갱신."
        action={
          <>
            <Button
              variant="outline"
              onClick={refetchAll}
              disabled={summary.isFetching || subs.isFetching}
            >
              <RefreshCcw
                className={cn(
                  "size-4",
                  summary.isFetching || subs.isFetching
                    ? "animate-spin"
                    : undefined,
                )}
              />
              새로고침
            </Button>
          </>
        }
      />

      {summary.error ? (
        <p className="text-sm text-destructive">{summary.error.message}</p>
      ) : null}

      <section
        className="grid grid-cols-1 gap-3 lg:grid-cols-2"
        aria-label="데이터 수집 4 섹션"
      >
        <SectionCard
          title="KIS WebSocket (실시간)"
          icon={<Wifi className="size-4" />}
          status={summary.data?.ws.status}
          loading={summary.isLoading}
          rows={[
            ["연결 상태", summary.data?.ws.connectionState ?? "-"],
            ["마지막 tick", formatKstDateTime(summary.data?.ws.lastTickAt)],
            ["마지막 connect", formatKstDateTime(summary.data?.ws.lastConnectedAt)],
            [
              "마지막 disconnect",
              formatKstDateTime(summary.data?.ws.lastDisconnectedAt),
            ],
            [
              "구독 종목 (현재/한도)",
              summary.data
                ? `${summary.data.ws.subscribedCount} / ${summary.data.ws.maxSubscriptions}`
                : "-",
            ],
            [
              "ad-hoc 추가",
              summary.data ? `${summary.data.ws.adhocCount} 종목` : "-",
            ],
            [
              "24h success",
              summary.data ? `${summary.data.ws.count24hOk} 건` : "-",
            ],
            [
              "24h down",
              summary.data ? `${summary.data.ws.count24hDown} 건` : "-",
            ],
            [
              "24h limit-exceeded",
              summary.data
                ? `${summary.data.ws.count24hLimitExceeded} 건`
                : "-",
            ],
          ]}
          latestEvent={summary.data?.ws.latestEvent}
          warning={
            summary.data &&
            summary.data.ws.subscribedCount >= summary.data.ws.maxSubscriptions
              ? "KIS WS 동시 구독 한도(41) 도달. 신규 종목 추가 거부됨."
              : null
          }
        />

        <SectionCard
          title="KIS REST (시세)"
          icon={<Activity className="size-4" />}
          status={summary.data?.rest.status}
          loading={summary.isLoading}
          rows={[
            [
              "최신 snapshot",
              formatKstDateTime(summary.data?.rest.lastSnapshotAt),
            ],
            [
              "최신 minute bar",
              formatKstDateTime(summary.data?.rest.lastMinuteBarAt),
            ],
          ]}
          hint="WS 와 같은 marketdata ops_event 채널을 공유. 분단위 적재 기준."
        />

        <SectionCard
          title="DART + 네이버 (뉴스·공시)"
          icon={<Newspaper className="size-4" />}
          status={summary.data?.content.status}
          loading={summary.isLoading}
          rows={[
            [
              "최신 뉴스",
              formatKstDateTime(summary.data?.content.lastNewsAt),
            ],
            [
              "최신 공시",
              formatKstDateTime(summary.data?.content.lastDisclosureAt),
            ],
            [
              "뉴스 24h ok/down",
              summary.data
                ? `${summary.data.content.newsCount24hOk} / ${summary.data.content.newsCount24hDown}`
                : "-",
            ],
            [
              "공시 24h ok/down",
              summary.data
                ? `${summary.data.content.disclosureCount24hOk} / ${summary.data.content.disclosureCount24hDown}`
                : "-",
            ],
          ]}
          latestEvent={summary.data?.content.latestEvent}
        />

        <SectionCard
          title="yfinance 매크로"
          icon={<Database className="size-4" />}
          status={summary.data?.macro.status}
          loading={summary.isLoading}
          rows={[
            ["최신 base date", summary.data?.macro.lastBaseDate ?? "-"],
            [
              "24h ok/down",
              summary.data
                ? `${summary.data.macro.count24hOk} / ${summary.data.macro.count24hDown}`
                : "-",
            ],
          ]}
          latestEvent={summary.data?.macro.latestEvent}
          hint="일별 데이터 — 2일 이상 멈춰있으면 지연, 7일 이상이면 down 처리"
        />
      </section>

      {/* ── KIS WS 종목 구독 관리 ── */}
      <Card>
        <CardHeader className="flex flex-col gap-1">
          <CardTitle className="text-base">KIS WS 구독 종목 관리</CardTitle>
          <CardDescription>
            watchlist 자동 구독 외에 운영자가 추가로 모니터링하고 싶은 종목을
            ad-hoc 등록합니다. 추가/제거는 collector-worker 의 다음 reconcile
            사이클 (60초 내) 에 KIS WS 에 반영됩니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <Input
              value={codeInput}
              onChange={(e) => setCodeInput(e.target.value)}
              placeholder="종목코드 (예: 005930, 000660). 공백/콤마 구분 다중 입력 가능."
              className="sm:max-w-md"
              disabled={subscribe.isPending}
            />
            <Button
              onClick={() => {
                void onAddSymbols();
              }}
              disabled={subscribe.isPending || !codeInput.trim()}
            >
              {subscribe.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Plus className="size-4" />
              )}
              ad-hoc 추가
            </Button>
          </div>

          {subs.data &&
          subs.data.totalCount >= subs.data.maxSubscriptions ? (
            <div className="flex items-center gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              <AlertTriangle className="size-4" />
              KIS WS 동시 구독 한도({subs.data.maxSubscriptions}) 도달 — 신규
              종목 추가가 즉시 반영되지 않을 수 있습니다.
            </div>
          ) : null}

          {subs.isLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> 구독 목록 불러오는 중…
            </div>
          ) : subs.error ? (
            <p className="text-sm text-destructive">{subs.error.message}</p>
          ) : !subs.data || subs.data.rows.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              현재 구독 중인 종목이 없습니다. (active 인스턴스 watchlist 자동 +
              ad-hoc 추가)
            </p>
          ) : (
            <div className="rounded-xl border">
              <div className="flex items-center justify-between gap-2 border-b px-3 py-2 text-xs text-muted-foreground">
                <span>
                  총 {subs.data.totalCount} / {subs.data.maxSubscriptions} 종목
                  · 연결 상태{" "}
                  <span className="font-medium">
                    {subs.data.connectionState}
                  </span>
                  {subs.data.lastTickAt
                    ? ` · 마지막 tick ${formatKstDateTime(subs.data.lastTickAt)}`
                    : ""}
                </span>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>종목코드</TableHead>
                    <TableHead>출처</TableHead>
                    <TableHead className="w-[1%]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {subs.data.rows.map((row) => (
                    <SubscriptionTableRow
                      key={`${row.symbolCode}:${row.source}`}
                      row={row}
                      onRemove={() => void onRemove(row.symbolCode)}
                      removing={pendingRemove === row.symbolCode}
                    />
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* ── ops_event 최근 이력 ── */}
      <Card>
        <CardHeader className="flex flex-col gap-1">
          <CardTitle className="text-base">최근 ops_event (서비스별)</CardTitle>
          <CardDescription>
            각 데이터 채널의 최근 10건. statusCode 별 색상 구분 (ok / down /
            limit-exceeded).
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <OpsEventsForService service="marketdata" label="KIS WS + REST 시세" />
          <OpsEventsForService service="news" label="네이버 뉴스" />
          <OpsEventsForService service="disclosure" label="DART 공시" />
          <OpsEventsForService service="macro" label="yfinance 매크로" />
        </CardContent>
      </Card>
    </div>
  );
}

// ─────────── components ───────────

function SectionCard({
  title,
  icon,
  status,
  loading,
  rows,
  latestEvent,
  warning,
  hint,
}: {
  title: string;
  icon: React.ReactNode;
  status?: DataCollectionStatus;
  loading?: boolean;
  rows: Array<[string, React.ReactNode]>;
  latestEvent?: OpsEventView | null;
  warning?: string | null;
  hint?: string;
}) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center justify-between gap-2 text-base">
          <span className="flex items-center gap-2">
            {icon}
            {title}
          </span>
          {loading ? (
            <Loader2 className="size-4 animate-spin text-muted-foreground" />
          ) : (
            <StatusBadge status={status ?? "unknown"} />
          )}
        </CardTitle>
        {hint ? <CardDescription>{hint}</CardDescription> : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <dl className="grid grid-cols-1 gap-x-4 gap-y-1.5 text-sm sm:grid-cols-2">
          {rows.map(([label, value]) => (
            <div key={label} className="flex items-center justify-between gap-2">
              <dt className="text-xs text-muted-foreground">{label}</dt>
              <dd className="text-right font-medium tabular-nums">{value}</dd>
            </div>
          ))}
        </dl>

        {warning ? (
          <div className="flex items-center gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            <AlertTriangle className="size-4" />
            {warning}
          </div>
        ) : null}

        {latestEvent ? (
          <div className="flex flex-col gap-1 rounded-md border bg-muted/40 px-3 py-2 text-xs">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium">최근 이벤트</span>
              <span className="text-muted-foreground">
                {formatKstDateTime(latestEvent.occurredAt)}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <OpsEventStatusBadge code={latestEvent.statusCode} />
              <span className="font-mono text-[11px] text-muted-foreground">
                {latestEvent.eventType}
              </span>
            </div>
            {latestEvent.message ? (
              <p className="line-clamp-2 text-muted-foreground">
                {latestEvent.message}
              </p>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function StatusBadge({ status }: { status: DataCollectionStatus }) {
  switch (status) {
    case "ok":
      return (
        <Badge className="border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300">
          <CheckCircle2 className="size-3" />
          정상
        </Badge>
      );
    case "delayed":
      return (
        <Badge className="border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300">
          <AlertTriangle className="size-3" />
          지연
        </Badge>
      );
    case "down":
      return (
        <Badge variant="destructive">
          <AlertCircle className="size-3" />
          중단
        </Badge>
      );
    default:
      return (
        <Badge variant="outline">
          <HelpCircle className="size-3" />
          unknown
        </Badge>
      );
  }
}

function OpsEventStatusBadge({ code }: { code: string }) {
  switch (code) {
    case "ok":
      return (
        <Badge className="border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300">
          ok
        </Badge>
      );
    case "down":
      return <Badge variant="destructive">down</Badge>;
    case "limit_exceeded":
      return (
        <Badge className="border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300">
          limit-exceeded
        </Badge>
      );
    default:
      return <Badge variant="outline">{code}</Badge>;
  }
}

function SubscriptionTableRow({
  row,
  onRemove,
  removing,
}: {
  row: WsSubscriptionRow;
  onRemove: () => void;
  removing: boolean;
}) {
  const isAdhoc = row.source === "adhoc" || row.source === "adhoc_pending";
  return (
    <TableRow>
      <TableCell className="font-mono text-xs">{row.symbolCode}</TableCell>
      <TableCell>
        {row.source === "watchlist" ? (
          <Badge variant="secondary">watchlist</Badge>
        ) : row.source === "adhoc" ? (
          <Badge className="border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-300">
            ad-hoc
          </Badge>
        ) : (
          <Badge variant="outline">ad-hoc · pending</Badge>
        )}
      </TableCell>
      <TableCell className="text-right">
        {isAdhoc ? (
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={removing}
            onClick={onRemove}
            aria-label={`${row.symbolCode} 구독 제거`}
          >
            {removing ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Trash2 className="size-4" />
            )}
          </Button>
        ) : (
          <span className="text-[11px] text-muted-foreground">자동</span>
        )}
      </TableCell>
    </TableRow>
  );
}

function OpsEventsForService({
  service,
  label,
}: {
  service: OpsEventService;
  label: string;
}) {
  const events = useOpsEvents(service, 5);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 text-xs">
        <span className="font-semibold">{label}</span>
        <span className="font-mono text-muted-foreground">({service})</span>
        {events.isFetching ? (
          <Loader2 className="size-3 animate-spin text-muted-foreground" />
        ) : null}
      </div>
      {events.error ? (
        <p className="text-xs text-destructive">{events.error.message}</p>
      ) : events.isLoading ? (
        <p className="text-xs text-muted-foreground">불러오는 중…</p>
      ) : !events.data || events.data.length === 0 ? (
        <p className="text-xs text-muted-foreground">기록 없음</p>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[180px]">발생</TableHead>
                <TableHead className="w-[110px]">상태</TableHead>
                <TableHead className="w-[180px]">event_type</TableHead>
                <TableHead>메시지</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {events.data.map((event) => (
                <TableRow key={event.id}>
                  <TableCell className="text-xs">
                    {formatKstDateTime(event.occurredAt)}
                  </TableCell>
                  <TableCell>
                    <OpsEventStatusBadge code={event.statusCode} />
                  </TableCell>
                  <TableCell className="font-mono text-[11px] text-muted-foreground">
                    {event.eventType}
                  </TableCell>
                  <TableCell className="text-xs">
                    {event.message ?? "-"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
