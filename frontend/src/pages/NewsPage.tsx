import {
  useState,
  type FormEvent,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { ArrowUpRight, Loader2, RefreshCw } from "lucide-react";
import { useCurrentUser } from "@/lib/auth";
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
import { useDisclosureList, useNewsList } from "@/hooks/use-news";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";
import { useSelectableStrategyInstances } from "@/hooks/use-strategy-instances";
import { useWatchlist } from "@/hooks/use-watchlist";
import type {
  ApiPagedMeta,
  DisclosureListItem,
  NewsListItem,
  NewsUsefulnessStatus,
} from "@/lib/api-types";
import { formatKstDateTime, formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 20;
const KST_TIME_ZONE = "Asia/Seoul";

const NEWS_STATUS_OPTIONS = [
  { value: "", label: "전체 상태" },
  { value: "useful", label: "유용" },
  { value: "not_useful", label: "비유용" },
  { value: "unclassified", label: "미판단" },
] as const;

type NewsTab = "news" | "disclosures";

interface NewsFilters {
  symbolCode: string;
  usefulnessStatus: string;
  keyword: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  size: number;
}

interface DisclosureFilters {
  symbolCode: string;
  dartCorpCode: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  size: number;
}

export default function NewsPage() {
  const { data: currentUser } = useCurrentUser();
  const { selectedInstanceId, setSelectedInstanceId } =
    useStrategyInstanceSelection();
  const { data: selectableInstances, isLoading: isInstancesLoading } =
    useSelectableStrategyInstances();
  const selectedInstance =
    selectableInstances?.find((instance) => instance.id === selectedInstanceId) ??
    null;
  const watchlistQuery = useWatchlist(currentUser ? selectedInstanceId : null);

  const [tab, setTab] = useState<NewsTab>("news");
  const [newsFilters, setNewsFilters] = useState<NewsFilters>(() =>
    createNewsFilters(),
  );
  const [appliedNewsFilters, setAppliedNewsFilters] = useState<NewsFilters>(() =>
    createNewsFilters(),
  );
  const [disclosureFilters, setDisclosureFilters] =
    useState<DisclosureFilters>(() => createDisclosureFilters());
  const [appliedDisclosureFilters, setAppliedDisclosureFilters] =
    useState<DisclosureFilters>(() => createDisclosureFilters());

  const [selectedNewsItem, setSelectedNewsItem] = useState<NewsListItem | null>(
    null,
  );
  const [selectedDisclosureItem, setSelectedDisclosureItem] =
    useState<DisclosureListItem | null>(null);

  const newsListQuery = useNewsList({
    strategyInstanceId: selectedInstanceId,
    symbolCode: normalizeTextFilter(appliedNewsFilters.symbolCode),
    usefulnessStatus: normalizeTextFilter(
      appliedNewsFilters.usefulnessStatus,
    ) as NewsUsefulnessStatus | undefined,
    dateFrom: appliedNewsFilters.dateFrom,
    dateTo: appliedNewsFilters.dateTo,
    q: normalizeTextFilter(appliedNewsFilters.keyword),
    page: appliedNewsFilters.page,
    size: appliedNewsFilters.size,
  });
  const disclosureListQuery = useDisclosureList({
    strategyInstanceId: selectedInstanceId,
    symbolCode: normalizeTextFilter(appliedDisclosureFilters.symbolCode),
    dartCorpCode: normalizeTextFilter(appliedDisclosureFilters.dartCorpCode),
    dateFrom: appliedDisclosureFilters.dateFrom,
    dateTo: appliedDisclosureFilters.dateTo,
    page: appliedDisclosureFilters.page,
    size: appliedDisclosureFilters.size,
  });

  const activeListQuery = tab === "news" ? newsListQuery : disclosureListQuery;
  const activeMeta = activeListQuery.data?.meta ?? null;
  const isActiveRefreshing =
    activeListQuery.isFetching && !activeListQuery.isPending;
  const activeSymbolCode =
    tab === "news" ? newsFilters.symbolCode : disclosureFilters.symbolCode;

  const onSubmitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (tab === "news") {
      const next = {
        ...newsFilters,
        symbolCode: newsFilters.symbolCode.trim(),
        keyword: newsFilters.keyword.trim(),
        page: 1,
      };
      setNewsFilters(next);
      setAppliedNewsFilters(next);
      return;
    }

    const next = {
      ...disclosureFilters,
      symbolCode: disclosureFilters.symbolCode.trim(),
      dartCorpCode: disclosureFilters.dartCorpCode.trim(),
      page: 1,
    };
    setDisclosureFilters(next);
    setAppliedDisclosureFilters(next);
  };

  const onResetFilters = () => {
    if (tab === "news") {
      const next = createNewsFilters();
      setNewsFilters(next);
      setAppliedNewsFilters(next);
      return;
    }

    const next = createDisclosureFilters();
    setDisclosureFilters(next);
    setAppliedDisclosureFilters(next);
  };

  const movePage = (nextPage: number) => {
    if (!activeMeta || nextPage < 1 || nextPage > activeMeta.totalPages) {
      return;
    }

    if (tab === "news") {
      setNewsFilters((prev) => ({ ...prev, page: nextPage }));
      setAppliedNewsFilters((prev) => ({ ...prev, page: nextPage }));
      return;
    }

    setDisclosureFilters((prev) => ({ ...prev, page: nextPage }));
    setAppliedDisclosureFilters((prev) => ({ ...prev, page: nextPage }));
  };

  const applyQuickSymbolFilter = (symbolCode: string) => {
    if (tab === "news") {
      const next = { ...newsFilters, symbolCode, page: 1 };
      setNewsFilters(next);
      setAppliedNewsFilters(next);
      return;
    }

    const next = { ...disclosureFilters, symbolCode, page: 1 };
    setDisclosureFilters(next);
    setAppliedDisclosureFilters(next);
  };

  const clearQuickSymbolFilter = () => {
    applyQuickSymbolFilter("");
  };

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold">뉴스·공시</h1>
          <p className="text-sm text-muted-foreground">
            트레이딩 판단에 사용되는 외부 정보를 최근 7일 기본 범위로 탐색합니다.
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
          active={tab === "news"}
          onClick={() => {
            setSelectedDisclosureItem(null);
            setTab("news");
          }}
          label="뉴스"
        />
        <TabButton
          active={tab === "disclosures"}
          onClick={() => {
            setSelectedNewsItem(null);
            setTab("disclosures");
          }}
          label="공시"
        />
        <span className="text-xs text-muted-foreground">
          전역 인스턴스 선택은 API 필터로 자동 반영됩니다.
        </span>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>조회 필터</CardTitle>
          <CardDescription>
            {tab === "news"
              ? "전략 인스턴스, 기간, 종목, 유용성 상태, 제목 검색어 기준으로 조회합니다."
              : "전략 인스턴스, 기간, 종목 코드, DART 법인 코드 기준으로 조회합니다."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={onSubmitFilters}>
            <div className="grid grid-cols-1 gap-4 xl:grid-cols-6">
              <FilterField label="전략 인스턴스" htmlFor="news-instance">
                <div className="relative">
                  <select
                    id="news-instance"
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

              <FilterField label="시작일" htmlFor="news-date-from">
                <Input
                  id="news-date-from"
                  type="date"
                  value={
                    tab === "news"
                      ? newsFilters.dateFrom
                      : disclosureFilters.dateFrom
                  }
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    if (tab === "news") {
                      setNewsFilters((prev) => ({ ...prev, dateFrom: nextValue }));
                      return;
                    }
                    setDisclosureFilters((prev) => ({
                      ...prev,
                      dateFrom: nextValue,
                    }));
                  }}
                />
              </FilterField>

              <FilterField label="종료일" htmlFor="news-date-to">
                <Input
                  id="news-date-to"
                  type="date"
                  value={
                    tab === "news" ? newsFilters.dateTo : disclosureFilters.dateTo
                  }
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    if (tab === "news") {
                      setNewsFilters((prev) => ({ ...prev, dateTo: nextValue }));
                      return;
                    }
                    setDisclosureFilters((prev) => ({
                      ...prev,
                      dateTo: nextValue,
                    }));
                  }}
                />
              </FilterField>

              <FilterField label="종목 코드" htmlFor="news-symbol-code">
                <Input
                  id="news-symbol-code"
                  placeholder="예: 005930"
                  value={
                    tab === "news"
                      ? newsFilters.symbolCode
                      : disclosureFilters.symbolCode
                  }
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    if (tab === "news") {
                      setNewsFilters((prev) => ({
                        ...prev,
                        symbolCode: nextValue,
                      }));
                      return;
                    }
                    setDisclosureFilters((prev) => ({
                      ...prev,
                      symbolCode: nextValue,
                    }));
                  }}
                />
              </FilterField>

              {tab === "news" ? (
                <>
                  <FilterField
                    label="유용성 상태"
                    htmlFor="news-usefulness-status"
                  >
                    <select
                      id="news-usefulness-status"
                      className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                      value={newsFilters.usefulnessStatus}
                      onChange={(event) => {
                        setNewsFilters((prev) => ({
                          ...prev,
                          usefulnessStatus: event.target.value,
                        }));
                      }}
                    >
                      {NEWS_STATUS_OPTIONS.map((option) => (
                        <option key={option.value || "all"} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </FilterField>

                  <FilterField label="제목 검색" htmlFor="news-keyword">
                    <Input
                      id="news-keyword"
                      placeholder="제목 키워드"
                      value={newsFilters.keyword}
                      onChange={(event) => {
                        setNewsFilters((prev) => ({
                          ...prev,
                          keyword: event.target.value,
                        }));
                      }}
                    />
                  </FilterField>
                </>
              ) : (
                <>
                  <FilterField label="DART 법인 코드" htmlFor="disclosure-dart">
                    <Input
                      id="disclosure-dart"
                      placeholder="예: 00126380"
                      value={disclosureFilters.dartCorpCode}
                      onChange={(event) => {
                        setDisclosureFilters((prev) => ({
                          ...prev,
                          dartCorpCode: event.target.value,
                        }));
                      }}
                    />
                  </FilterField>

                  <div className="flex items-end">
                    <p className="text-xs text-muted-foreground">
                      공시 API는 제목 검색이나 상태 필터를 제공하지 않습니다.
                    </p>
                  </div>
                </>
              )}
            </div>

            <div className="flex flex-wrap items-center justify-end gap-2">
              <Button type="button" variant="outline" onClick={onResetFilters}>
                기본값 복원
              </Button>
              <Button type="submit">조회</Button>
            </div>
          </form>

          {selectedInstanceId && (
            <WatchlistQuickFilterCard
              instanceName={selectedInstance?.name ?? selectedInstanceId}
              activeSymbolCode={activeSymbolCode}
              currentUserLoaded={currentUser !== undefined}
              isLoggedIn={!!currentUser}
              isLoading={watchlistQuery.isLoading}
              errorMessage={watchlistQuery.error?.message ?? null}
              rows={watchlistQuery.data ?? []}
              fallbackCount={selectedInstance?.watchlistCount ?? null}
              onSelect={applyQuickSymbolFilter}
              onClear={clearQuickSymbolFilter}
            />
          )}
        </CardContent>
      </Card>

      {tab === "news" ? (
        <NewsTable query={newsListQuery} onRowClick={setSelectedNewsItem} />
      ) : (
        <DisclosureTable
          query={disclosureListQuery}
          onRowClick={setSelectedDisclosureItem}
        />
      )}

      <PaginationBar meta={activeMeta} onMovePage={movePage} />

      <NewsDetailDialog
        item={selectedNewsItem}
        open={selectedNewsItem !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedNewsItem(null);
          }
        }}
      />

      <DisclosureDetailDialog
        item={selectedDisclosureItem}
        open={selectedDisclosureItem !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedDisclosureItem(null);
          }
        }}
      />
    </div>
  );
}

function NewsTable({
  query,
  onRowClick,
}: {
  query: ReturnType<typeof useNewsList>;
  onRowClick: (item: NewsListItem) => void;
}) {
  if (query.isPending && !query.data) {
    return <LoadingCard label="뉴스 목록을 불러오는 중..." />;
  }

  if (query.error) {
    return (
      <ErrorCard message={query.error.message} onRetry={() => void query.refetch()} />
    );
  }

  if (!query.data || query.data.data.length === 0) {
    return <EmptyCard message="조회 조건에 맞는 뉴스가 없습니다." />;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>뉴스 목록</CardTitle>
        <CardDescription>
          행을 클릭하면 요약과 관련 종목, 외부 기사 링크를 확인할 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>발행 시각</TableHead>
              <TableHead>관련 종목</TableHead>
              <TableHead>제목</TableHead>
              <TableHead>요약</TableHead>
              <TableHead>유용성</TableHead>
              <TableHead>링크</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.data.map((row) => (
              <ClickableTableRow
                key={row.id}
                onClick={() => onRowClick(row)}
                label={`뉴스 ${row.title} 상세 열기`}
              >
                <TableCell>{formatKstDateTime(row.publishedAt)}</TableCell>
                <TableCell className="max-w-[220px] whitespace-normal">
                  <RelatedAssetList assets={row.relatedAssets} />
                </TableCell>
                <TableCell className="max-w-[320px] whitespace-normal">
                  <div className="flex flex-col gap-1">
                    <span className="font-medium">{row.title}</span>
                    <span className="text-xs text-muted-foreground">
                      제공처: {row.providerName}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="max-w-[360px] whitespace-normal text-muted-foreground">
                  {row.summary ?? "요약 없음"}
                </TableCell>
                <TableCell>
                  <NewsUsefulnessBadge status={row.usefulnessStatus} />
                </TableCell>
                <TableCell>
                  <ExternalLinkAnchor href={row.articleUrl} label="기사" />
                </TableCell>
              </ClickableTableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function DisclosureTable({
  query,
  onRowClick,
}: {
  query: ReturnType<typeof useDisclosureList>;
  onRowClick: (item: DisclosureListItem) => void;
}) {
  if (query.isPending && !query.data) {
    return <LoadingCard label="공시 목록을 불러오는 중..." />;
  }

  if (query.error) {
    return (
      <ErrorCard message={query.error.message} onRetry={() => void query.refetch()} />
    );
  }

  if (!query.data || query.data.data.length === 0) {
    return <EmptyCard message="조회 조건에 맞는 공시가 없습니다." />;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>공시 목록</CardTitle>
        <CardDescription>
          행을 클릭하면 본문 미리보기와 DART 링크를 확인할 수 있습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>발행 시각</TableHead>
              <TableHead>종목</TableHead>
              <TableHead>제목</TableHead>
              <TableHead>본문 미리보기</TableHead>
              <TableHead>링크</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.data.map((row) => (
              <ClickableTableRow
                key={row.id}
                onClick={() => onRowClick(row)}
                label={`공시 ${row.title} 상세 열기`}
              >
                <TableCell>{formatKstDateTime(row.publishedAt)}</TableCell>
                <TableCell className="max-w-[220px] whitespace-normal">
                  <div className="flex flex-col gap-1">
                    <span className="font-medium">
                      {row.symbolName ?? row.dartCorpCode}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {row.symbolCode ? `${row.symbolCode} · ` : ""}
                      DART {row.dartCorpCode}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="max-w-[320px] whitespace-normal font-medium">
                  {row.title}
                </TableCell>
                <TableCell className="max-w-[360px] whitespace-normal text-muted-foreground">
                  {row.previewText ?? "미리보기 없음"}
                </TableCell>
                <TableCell>
                  <ExternalLinkAnchor href={row.documentUrl} label="공시" />
                </TableCell>
              </ClickableTableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function WatchlistQuickFilterCard({
  instanceName,
  activeSymbolCode,
  currentUserLoaded,
  isLoggedIn,
  isLoading,
  errorMessage,
  rows,
  fallbackCount,
  onSelect,
  onClear,
}: {
  instanceName: string;
  activeSymbolCode: string;
  currentUserLoaded: boolean;
  isLoggedIn: boolean;
  isLoading: boolean;
  errorMessage: string | null;
  rows: { symbolCode: string; symbolName: string | null }[];
  fallbackCount: number | null;
  onSelect: (symbolCode: string) => void;
  onClear: () => void;
}) {
  const totalCount = rows.length > 0 ? rows.length : fallbackCount;

  return (
    <div className="mt-4 flex flex-col gap-3 rounded-xl border bg-muted/20 p-4">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium">감시종목 빠른 필터</span>
            <Badge variant="outline">{instanceName}</Badge>
            {totalCount !== null && (
              <Badge variant="outline">감시종목 {formatNumber(totalCount)}개</Badge>
            )}
          </div>
          <p className="text-xs text-muted-foreground">
            선택된 전략 인스턴스는 API 기본 필터로 적용되며, 아래 버튼으로 종목을
            추가 좁힘할 수 있습니다.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={onClear}
          disabled={!activeSymbolCode}
        >
          종목 해제
        </Button>
      </div>

      {!currentUserLoaded || isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          감시종목을 불러오는 중...
        </div>
      ) : !isLoggedIn ? (
        <p className="text-sm text-muted-foreground">
          비로그인 상태에서는 감시종목 버튼 목록을 불러올 수 없어, 인스턴스 기본
          필터만 적용합니다.
        </p>
      ) : errorMessage ? (
        <p className="text-sm text-destructive">
          감시종목 조회 실패: {errorMessage}
        </p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          등록된 감시종목이 없습니다.
        </p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {rows.map((row) => {
            const active = activeSymbolCode === row.symbolCode;
            return (
              <Button
                key={row.symbolCode}
                type="button"
                variant={active ? "default" : "outline"}
                size="sm"
                onClick={() => onSelect(row.symbolCode)}
              >
                {row.symbolName ?? row.symbolCode}
                {row.symbolName ? (
                  <span className="text-xs opacity-80">{row.symbolCode}</span>
                ) : null}
              </Button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function NewsDetailDialog({
  item,
  open,
  onOpenChange,
}: {
  item: NewsListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[88vh] max-w-4xl overflow-hidden p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>뉴스 상세</DialogTitle>
          <DialogDescription>
            목록 응답 기준으로 제목, 요약, 관련 종목, 외부 기사 링크를 표시합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="overflow-y-auto px-6 py-5">
          {item ? <NewsDetailContent item={item} /> : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function DisclosureDetailDialog({
  item,
  open,
  onOpenChange,
}: {
  item: DisclosureListItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[88vh] max-w-4xl overflow-hidden p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>공시 상세</DialogTitle>
          <DialogDescription>
            목록 응답 기준으로 종목, 미리보기, DART 링크를 표시합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="overflow-y-auto px-6 py-5">
          {item ? <DisclosureDetailContent item={item} /> : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function NewsDetailContent({ item }: { item: NewsListItem }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <DetailField label="제목" value={item.title} />
        <DetailField label="제공처" value={item.providerName} />
        <DetailField
          label="유용성 상태"
          value={<NewsUsefulnessBadge status={item.usefulnessStatus} />}
        />
        <DetailField label="발행 시각" value={formatKstDateTime(item.publishedAt)} />
      </div>

      <Card size="sm">
        <CardHeader>
          <CardTitle>관련 종목</CardTitle>
        </CardHeader>
        <CardContent>
          <RelatedAssetList assets={item.relatedAssets} />
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle>요약</CardTitle>
        </CardHeader>
        <CardContent>
          <TextPreview value={item.summary} />
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle>외부 기사 링크</CardTitle>
        </CardHeader>
        <CardContent>
          <ExternalLinkAnchor href={item.articleUrl} label="원문 열기" />
        </CardContent>
      </Card>
    </div>
  );
}

function DisclosureDetailContent({ item }: { item: DisclosureListItem }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <DetailField label="제목" value={item.title} />
        <DetailField
          label="종목"
          value={
            item.symbolName
              ? `${item.symbolName}${item.symbolCode ? ` (${item.symbolCode})` : ""}`
              : item.symbolCode
          }
        />
        <DetailField label="DART 법인 코드" value={item.dartCorpCode} />
        <DetailField label="발행 시각" value={formatKstDateTime(item.publishedAt)} />
      </div>

      <Card size="sm">
        <CardHeader>
          <CardTitle>본문 미리보기</CardTitle>
        </CardHeader>
        <CardContent>
          <TextPreview value={item.previewText} />
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle>DART 링크</CardTitle>
        </CardHeader>
        <CardContent>
          <ExternalLinkAnchor href={item.documentUrl} label="공시 원문 열기" />
        </CardContent>
      </Card>
    </div>
  );
}

function RelatedAssetList({
  assets,
}: {
  assets: { symbolCode: string; symbolName: string | null }[];
}) {
  if (assets.length === 0) {
    return <span className="text-sm text-muted-foreground">연결 종목 없음</span>;
  }

  return (
    <div className="flex flex-wrap gap-1.5">
      {assets.map((asset) => (
        <Badge
          key={`${asset.symbolCode}-${asset.symbolName ?? "unknown"}`}
          variant="outline"
        >
          {asset.symbolName ?? asset.symbolCode}
          {asset.symbolName ? (
            <span className="text-[11px] opacity-80">{asset.symbolCode}</span>
          ) : null}
        </Badge>
      ))}
    </div>
  );
}

function ExternalLinkAnchor({
  href,
  label,
}: {
  href: string;
  label: string;
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      onClick={(event) => {
        event.stopPropagation();
      }}
      className="inline-flex items-center gap-1 text-sm font-medium text-primary underline-offset-4 hover:underline"
    >
      {label}
      <ArrowUpRight className="size-3.5" />
    </a>
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

function TextPreview({ value }: { value: string | null | undefined }) {
  if (!value) {
    return <p className="text-sm text-muted-foreground">-</p>;
  }

  return (
    <div className="rounded-lg bg-muted/40 p-3 text-sm whitespace-pre-wrap break-all">
      {value}
    </div>
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

function NewsUsefulnessBadge({
  status,
}: {
  status: NewsUsefulnessStatus;
}) {
  const variant: "default" | "destructive" | "outline" =
    status === "useful"
      ? "default"
      : status === "not_useful"
        ? "destructive"
        : "outline";

  const label =
    status === "useful"
      ? "유용"
      : status === "not_useful"
        ? "비유용"
        : status === "unclassified"
          ? "미판단"
          : status;

  return <Badge variant={variant}>{label}</Badge>;
}

function normalizeTextFilter(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function createNewsFilters(): NewsFilters {
  const range = getDefaultDateRange();

  return {
    symbolCode: "",
    usefulnessStatus: "",
    keyword: "",
    dateFrom: range.dateFrom,
    dateTo: range.dateTo,
    page: 1,
    size: PAGE_SIZE,
  };
}

function createDisclosureFilters(): DisclosureFilters {
  const range = getDefaultDateRange();

  return {
    symbolCode: "",
    dartCorpCode: "",
    dateFrom: range.dateFrom,
    dateTo: range.dateTo,
    page: 1,
    size: PAGE_SIZE,
  };
}

function getDefaultDateRange() {
  const kstToday = getKstCalendarDate(new Date());
  const endDate = new Date(
    Date.UTC(kstToday.year, kstToday.month - 1, kstToday.day),
  );
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
