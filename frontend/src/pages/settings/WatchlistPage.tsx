import { useDeferredValue, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Loader2, Plus, Search, Trash2, X } from "lucide-react";
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
import FormField from "@/components/settings/FormField";
import SettingsHeader from "@/components/settings/SettingsHeader";
import { useAssetMasters } from "@/hooks/use-asset-masters";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";
import {
  useAddWatchlist,
  useRemoveWatchlist,
  useWatchlist,
} from "@/hooks/use-watchlist";
import { formatKstDateTime } from "@/lib/format";

export default function WatchlistPage() {
  const params = useParams();
  const instanceId = params.id ?? "";
  const instances = useStrategyInstances();
  const instance = instances.data?.find((i) => i.id === instanceId);

  const watchlist = useWatchlist(instanceId);
  const add = useAddWatchlist(instanceId);
  const remove = useRemoveWatchlist(instanceId);

  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query.trim());
  const [pendingAdd, setPendingAdd] = useState<string | null>(null);
  const [pendingRemove, setPendingRemove] = useState<string | null>(null);
  const search = useAssetMasters(
    {
      q: deferredQuery,
      hidden: false,
    },
    {
      enabled: deferredQuery.length > 0,
    },
  );

  const watchlistIds = new Set(
    watchlist.data?.map((row) => row.assetMasterId) ?? [],
  );
  const hasQuery = deferredQuery.length > 0;

  const onAdd = async (assetMasterId: string) => {
    if (watchlistIds.has(assetMasterId)) {
      toast.message("이미 감시 종목에 추가된 자산입니다.");
      return;
    }

    setPendingAdd(assetMasterId);
    try {
      await add.mutateAsync({
        instanceId,
        body: { assetMasterId },
      });
      toast.success("감시 종목이 추가되었습니다.");
    } finally {
      setPendingAdd(null);
    }
  };

  const searchDescription = (() => {
    if (!hasQuery) {
      return "종목코드 또는 종목명을 입력해 자산 마스터를 검색하세요.";
    }
    if (search.isLoading) {
      return "검색 결과를 불러오는 중입니다.";
    }
    if (search.error) {
      return search.error.message;
    }
    if (!search.data || search.data.length === 0) {
      return "조건에 맞는 자산 마스터가 없습니다.";
    }
    return `검색 결과 ${search.data.length}건`;
  })();

  const onRemove = async (assetMasterId: string) => {
    setPendingRemove(assetMasterId);
    try {
      await remove.mutateAsync({ instanceId, assetMasterId });
      toast.success("감시 종목이 제거되었습니다.");
    } finally {
      setPendingRemove(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title={`감시 종목${instance ? ` · ${instance.name}` : ""}`}
        description="이 인스턴스가 매 사이클마다 평가할 종목 목록입니다."
        action={
          <Button variant="outline" render={<Link to="/settings/instances" />}>
            <ArrowLeft className="size-4" /> 인스턴스 목록
          </Button>
        }
      />

      <section className="flex flex-col gap-3 rounded-xl border p-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold">감시 종목 추가</h2>
          {search.isFetching && hasQuery ? (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Loader2 className="size-3 animate-spin" /> 검색 중...
            </span>
          ) : null}
        </div>

        <div className="flex flex-col gap-3">
          <FormField
            id="watchlist-search"
            label="자산 마스터 검색"
            helpText="숨김 자산은 제외됩니다."
          >
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="watchlist-search"
                value={query}
                className="pl-8 pr-10"
                placeholder="005930, 삼성전자"
                onChange={(event) => setQuery(event.target.value)}
              />
              {query ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="absolute top-1/2 right-1 -translate-y-1/2"
                  aria-label="검색어 지우기"
                  onClick={() => setQuery("")}
                >
                  <X className="size-4" />
                </Button>
              ) : null}
            </div>
          </FormField>

          <p
            className={
              search.error
                ? "text-sm text-destructive"
                : "text-sm text-muted-foreground"
            }
          >
            {searchDescription}
          </p>

          {hasQuery && search.data && search.data.length > 0 ? (
            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>종목코드</TableHead>
                    <TableHead>종목명</TableHead>
                    <TableHead>시장 구분</TableHead>
                    <TableHead>상태</TableHead>
                    <TableHead className="w-[1%]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {search.data.map((row) => {
                    const alreadyAdded = watchlistIds.has(row.id);
                    const isPending = pendingAdd === row.id;

                    return (
                      <TableRow key={row.id}>
                        <TableCell className="font-mono text-xs">
                          {row.symbolCode}
                        </TableCell>
                        <TableCell className="font-medium">
                          {row.symbolName}
                        </TableCell>
                        <TableCell>{row.marketType}</TableCell>
                        <TableCell>
                          {alreadyAdded ? (
                            <Badge variant="secondary">이미 추가됨</Badge>
                          ) : (
                            <Badge>추가 가능</Badge>
                          )}
                        </TableCell>
                        <TableCell>
                          <Button
                            type="button"
                            size="sm"
                            disabled={alreadyAdded || add.isPending}
                            onClick={() => {
                              void onAdd(row.id);
                            }}
                          >
                            {isPending ? (
                              <Loader2 className="size-4 animate-spin" />
                            ) : (
                              <Plus className="size-4" />
                            )}
                            추가
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          ) : null}
        </div>
      </section>

      {watchlist.isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중...
        </div>
      ) : watchlist.error ? (
        <p className="text-sm text-destructive">{watchlist.error.message}</p>
      ) : !watchlist.data || watchlist.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          감시 중인 종목이 없습니다.
        </p>
      ) : (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>종목코드</TableHead>
                <TableHead>종목명</TableHead>
                <TableHead>자산 마스터 ID</TableHead>
                <TableHead>추가일</TableHead>
                <TableHead className="w-[1%]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {watchlist.data.map((row) => (
                <TableRow key={row.assetMasterId}>
                  <TableCell className="font-mono">{row.symbolCode}</TableCell>
                  <TableCell>{row.symbolName ?? "-"}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {row.assetMasterId}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatKstDateTime(row.addedAt)}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="제거"
                      disabled={pendingRemove === row.assetMasterId}
                      onClick={() => {
                        void onRemove(row.assetMasterId);
                      }}
                    >
                      {pendingRemove === row.assetMasterId ? (
                        <Loader2 className="size-4 animate-spin" />
                      ) : (
                        <Trash2 className="size-4" />
                      )}
                    </Button>
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
