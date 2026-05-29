import { useMemo, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Eye,
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import FormField from "@/components/settings/FormField";
import SettingsHeader from "@/components/settings/SettingsHeader";
import { useAuditLog } from "@/hooks/use-audit-log";
import { formatKstDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { AuditLogFilter, AuditLogRow } from "@/lib/api-types";

/**
 * audit_log read-only 페이지네이션 + 필터 (F4 신규).
 *
 * 백엔드: AuditLogAdminController.GET /api/admin/audit-log
 *
 * - 컬럼: 발생, actor (type + id), target (type + id), action, before/after json (요약)
 * - 필터: from / to / targetType / actorType / actionType
 * - row 클릭 → dialog 에 before/after/summary JSON 펼치기
 */

const PAGE_SIZE = 50;

export default function AuditLogPage() {
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");
  const [targetType, setTargetType] = useState<string>("");
  const [actorType, setActorType] = useState<string>("");
  const [actionType, setActionType] = useState<string>("");
  const [applied, setApplied] = useState<{
    targetType: string;
    actorType: string;
    actionType: string;
  }>({ targetType: "", actorType: "", actionType: "" });
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<AuditLogRow | null>(null);

  const filter: AuditLogFilter = useMemo(
    () => ({
      page,
      size: PAGE_SIZE,
      from: from ? toBackendIso(from) : null,
      to: to ? toBackendIso(to) : null,
      targetType: applied.targetType || null,
      actorType: applied.actorType || null,
      actionType: applied.actionType || null,
    }),
    [page, from, to, applied],
  );

  const log = useAuditLog(filter);

  const applyFilters = () => {
    setApplied({
      targetType: targetType.trim(),
      actorType: actorType.trim(),
      actionType: actionType.trim(),
    });
    setPage(0);
  };

  const resetFilters = () => {
    setFrom("");
    setTo("");
    setTargetType("");
    setActorType("");
    setActionType("");
    setApplied({ targetType: "", actorType: "", actionType: "" });
    setPage(0);
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="audit-log"
        description="운영자 / 시스템 변경 이벤트 read-only 페이지네이션. 모든 admin write 동작은 audit_log 에 자동 기록됩니다."
        action={
          <Button
            variant="outline"
            onClick={() => void log.refetch()}
            disabled={log.isFetching}
          >
            <RefreshCcw
              className={cn(
                "size-4",
                log.isFetching ? "animate-spin" : undefined,
              )}
            />
            새로고침
          </Button>
        }
      />

      {/* 필터 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">필터</CardTitle>
          <CardDescription>
            from / to 는 KST. type 필드는 정확 매칭.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 lg:grid-cols-5">
          <FormField id="al-from" label="from (occurredAt)">
            <Input
              id="al-from"
              type="datetime-local"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </FormField>
          <FormField id="al-to" label="to (occurredAt)">
            <Input
              id="al-to"
              type="datetime-local"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </FormField>
          <FormField id="al-target" label="targetType">
            <Input
              id="al-target"
              value={targetType}
              placeholder="예: STRATEGY_INSTANCE"
              onChange={(e) => setTargetType(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") applyFilters();
              }}
            />
          </FormField>
          <FormField id="al-actor" label="actorType">
            <Input
              id="al-actor"
              value={actorType}
              placeholder="예: APP_USER"
              onChange={(e) => setActorType(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") applyFilters();
              }}
            />
          </FormField>
          <FormField id="al-action" label="actionType">
            <Input
              id="al-action"
              value={actionType}
              placeholder="예: UPDATE, LIFECYCLE_TRANSITION"
              onChange={(e) => setActionType(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") applyFilters();
              }}
            />
          </FormField>

          <div className="flex items-end gap-2 lg:col-span-5">
            <Button onClick={applyFilters}>
              <Search className="size-4" /> 필터 적용
            </Button>
            <Button variant="outline" onClick={resetFilters}>
              <X className="size-4" /> 초기화
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 표 */}
      <Card>
        <CardContent className="px-2 py-3">
          {log.isLoading && !log.data ? (
            <div className="flex items-center gap-2 px-3 py-8 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> audit-log 불러오는 중…
            </div>
          ) : log.error ? (
            <p className="px-3 py-4 text-sm text-destructive">
              {log.error.message}
            </p>
          ) : !log.data || log.data.rows.length === 0 ? (
            <p className="px-3 py-8 text-center text-sm text-muted-foreground">
              조회 조건에 매칭되는 audit_log row 가 없습니다.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[180px]">발생 (KST)</TableHead>
                    <TableHead>actor</TableHead>
                    <TableHead>target</TableHead>
                    <TableHead>action</TableHead>
                    <TableHead>before</TableHead>
                    <TableHead>after</TableHead>
                    <TableHead className="w-[1%]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {log.data.rows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="text-xs">
                        {formatKstDateTime(row.occurredAt)}
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary">{row.actorType}</Badge>
                        <span className="ml-2 font-mono text-[11px] text-muted-foreground">
                          {row.actorId
                            ? row.actorId.slice(0, 8)
                            : "—"}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{row.targetType}</Badge>
                        <span className="ml-2 font-mono text-[11px] text-muted-foreground">
                          {row.targetId
                            ? row.targetId.slice(0, 8)
                            : "—"}
                        </span>
                      </TableCell>
                      <TableCell className="font-mono text-xs">
                        {row.actionType}
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate text-[11px] text-muted-foreground">
                        {row.beforeJson ?? "—"}
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate text-[11px] text-muted-foreground">
                        {row.afterJson ?? "—"}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label="상세 보기"
                          onClick={() => setDetail(row)}
                        >
                          <Eye className="size-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 페이지네이션 */}
      {log.data && log.data.totalPages > 0 ? (
        <div className="flex items-center justify-between gap-2 px-1 text-sm">
          <span className="text-muted-foreground">
            총 {log.data.totalElements.toLocaleString("ko-KR")} 건 · 페이지{" "}
            {log.data.page + 1} / {log.data.totalPages}
          </span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={log.data.page <= 0 || log.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="size-4" /> 이전
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={
                log.data.page >= log.data.totalPages - 1 || log.isFetching
              }
              onClick={() => setPage((p) => p + 1)}
            >
              다음 <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      ) : null}

      {/* 상세 dialog */}
      <Dialog open={detail !== null} onOpenChange={(o) => !o && setDetail(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>audit_log 상세</DialogTitle>
            {detail ? (
              <DialogDescription>
                {formatKstDateTime(detail.occurredAt)} · {detail.actorType} →{" "}
                {detail.targetType} ({detail.actionType})
              </DialogDescription>
            ) : null}
          </DialogHeader>
          {detail ? (
            <div className="flex flex-col gap-4">
              <JsonBlock label="before" value={detail.beforeJson} />
              <JsonBlock label="after" value={detail.afterJson} />
              <JsonBlock label="summary" value={detail.summaryJson} />
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function JsonBlock({
  label,
  value,
}: {
  label: string;
  value: string | null;
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-muted-foreground uppercase">
        {label}
      </span>
      <pre className="overflow-x-auto rounded-lg bg-muted/50 p-3 font-mono text-xs whitespace-pre-wrap break-words">
        {value ?? "—"}
      </pre>
    </div>
  );
}

function toBackendIso(value: string): string {
  if (!value) return value;
  if (/[+-]\d{2}:\d{2}$/.test(value) || value.endsWith("Z")) return value;
  return `${value}:00+09:00`;
}
