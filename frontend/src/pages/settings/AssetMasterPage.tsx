import { useDeferredValue, useState } from "react";
import { Loader2, Pencil, Plus, Search, X } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import AssetMasterDialog from "@/components/settings/AssetMasterDialog";
import FormField from "@/components/settings/FormField";
import SettingsHeader from "@/components/settings/SettingsHeader";
import { useAssetMasters } from "@/hooks/use-asset-masters";
import { formatKstDateTime } from "@/lib/format";
import type { AssetMaster } from "@/lib/api-types";

type HiddenFilterValue = "all" | "visible" | "hidden";

/**
 * 글로벌 종목 마스터 화면 — docs/04-api-spec.md §8.19~8.20, spec2.md §7.6.
 */
export default function AssetMasterPage() {
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query.trim());
  const [hiddenFilter, setHiddenFilter] = useState<HiddenFilterValue>("all");
  const [dialog, setDialog] = useState<
    | { mode: "create" }
    | { mode: "edit"; asset: AssetMaster }
    | null
  >(null);

  const hidden =
    hiddenFilter === "all" ? undefined : hiddenFilter === "hidden";
  const { data, isLoading, isFetching, error } = useAssetMasters({
    q: deferredQuery || undefined,
    hidden,
  });

  const hasFilters = deferredQuery.length > 0 || hiddenFilter !== "all";

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="자산 마스터"
        description="글로벌 종목 코드, 종목명, 시장 구분, DART 기업코드 매핑을 관리합니다."
        action={
          <Button onClick={() => setDialog({ mode: "create" })}>
            <Plus className="size-4" /> 추가
          </Button>
        }
      />

      <section className="flex flex-col gap-3 rounded-xl border p-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold">검색 및 필터</h2>
          {isFetching && !isLoading ? (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Loader2 className="size-3 animate-spin" /> 갱신 중...
            </span>
          ) : null}
        </div>

        <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_180px_auto] md:items-end">
          <FormField
            id="asset-search"
            label="검색"
            helpText="종목코드 또는 종목명으로 검색합니다."
          >
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="asset-search"
                value={query}
                className="pl-8"
                placeholder="005930, 삼성전자"
                onChange={(event) => setQuery(event.target.value)}
              />
            </div>
          </FormField>

          <FormField id="asset-hidden-filter" label="숨김 필터">
            <select
              id="asset-hidden-filter"
              value={hiddenFilter}
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              onChange={(event) =>
                setHiddenFilter(event.target.value as HiddenFilterValue)
              }
            >
              <option value="all">전체</option>
              <option value="visible">노출만</option>
              <option value="hidden">숨김만</option>
            </select>
          </FormField>

          <Button
            type="button"
            variant="outline"
            disabled={!hasFilters}
            onClick={() => {
              setQuery("");
              setHiddenFilter("all");
            }}
          >
            <X className="size-4" /> 초기화
          </Button>
        </div>
      </section>

      {isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중...
        </div>
      ) : error ? (
        <p className="text-sm text-destructive">{error.message}</p>
      ) : !data || data.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {hasFilters
            ? "조건에 맞는 자산 마스터가 없습니다."
            : "등록된 자산 마스터가 없습니다."}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">
            총 {data.length}건
            {hasFilters ? "이 조회되었습니다." : "이 등록되어 있습니다."}
          </p>

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>종목코드</TableHead>
                  <TableHead>종목명</TableHead>
                  <TableHead>시장 구분</TableHead>
                  <TableHead>DART 기업코드</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>수정일</TableHead>
                  <TableHead className="w-[1%]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="font-mono text-xs">
                      {row.symbolCode}
                    </TableCell>
                    <TableCell className="font-medium">
                      {row.symbolName}
                    </TableCell>
                    <TableCell>{row.marketType}</TableCell>
                    <TableCell className="font-mono text-xs">
                      {row.dartCorpCode ?? "-"}
                    </TableCell>
                    <TableCell>
                      {row.hidden ? (
                        <Badge variant="secondary">숨김</Badge>
                      ) : (
                        <Badge>노출</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatKstDateTime(row.updatedAt)}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label="수정"
                        onClick={() => setDialog({ mode: "edit", asset: row })}
                      >
                        <Pencil className="size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}

      <AssetMasterDialog
        key={dialog ? (dialog.mode === "edit" ? dialog.asset.id : "create") : "closed"}
        open={dialog !== null}
        mode={dialog?.mode ?? "create"}
        asset={dialog?.mode === "edit" ? dialog.asset : undefined}
        onOpenChange={(open) => {
          if (!open) setDialog(null);
        }}
        onSuccess={(action) => {
          toast.success(
            action === "create"
              ? "자산 마스터가 추가되었습니다."
              : "자산 마스터가 수정되었습니다.",
          );
          setDialog(null);
        }}
      />
    </div>
  );
}
