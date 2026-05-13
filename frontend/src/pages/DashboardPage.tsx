import { Loader2 } from "lucide-react";
import InstanceDashboardSection from "@/components/dashboard/InstanceDashboardSection";
import StrategyOverviewSection from "@/components/dashboard/StrategyOverviewSection";
import SystemStatusSection from "@/components/dashboard/SystemStatusSection";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useInstanceDashboard } from "@/hooks/use-dashboard";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";
import {
  useSelectableStrategyInstances,
  type StrategyInstanceSelectorOption,
} from "@/hooks/use-strategy-instances";
import type { InstanceDashboard } from "@/lib/api-types";

/**
 * 대시보드 페이지 — docs/spec2.md §7.1.
 *
 * 현재 범위:
 * - 전략 오버뷰 (인스턴스 미선택 fallback) + 시스템 상태 카드
 * - 인스턴스 단위 상세(요약 KPI, 보유종목, 최근 활동)
 *
 * 30초 자동 갱신은 hook 의 `refetchInterval` 로 처리된다.
 */
export default function DashboardPage() {
  const { isGlobalSelection, selectedInstanceId } =
    useStrategyInstanceSelection();
  const {
    data: selectableInstances,
    isLoading: isSelectionLoading,
  } = useSelectableStrategyInstances();
  const selectedInstance =
    selectableInstances?.find((instance) => instance.id === selectedInstanceId) ??
    null;
  const instanceDashboardQuery = useInstanceDashboard(
    isGlobalSelection ? null : selectedInstanceId,
  );

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">대시보드</h1>
        <p className="text-sm text-muted-foreground">
          전략 인스턴스별 운영 상태를 한눈에 확인합니다.
        </p>
      </header>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1fr_360px]">
        {isGlobalSelection ? (
          <StrategyOverviewSection />
        ) : (
          <SelectedInstanceSummaryCard
            selectedInstanceId={selectedInstanceId}
            selectedInstance={selectedInstance}
            dashboard={instanceDashboardQuery.data ?? null}
            isLoading={isSelectionLoading || instanceDashboardQuery.isLoading}
          />
        )}
        <SystemStatusSection />
      </div>

      {!isGlobalSelection && selectedInstanceId && (
        <InstanceDashboardSection
          selectedInstanceId={selectedInstanceId}
          selectedInstanceName={selectedInstance?.name ?? null}
          dashboard={instanceDashboardQuery.data ?? null}
          isLoading={instanceDashboardQuery.isLoading}
          error={instanceDashboardQuery.error ?? null}
        />
      )}
    </div>
  );
}

function SelectedInstanceSummaryCard({
  selectedInstanceId,
  selectedInstance,
  dashboard,
  isLoading,
}: {
  selectedInstanceId: string | null;
  selectedInstance: StrategyInstanceSelectorOption | null;
  dashboard: InstanceDashboard | null;
  isLoading: boolean;
}) {
  const instanceMeta = dashboard?.instance ?? selectedInstance;

  if (isLoading && !instanceMeta) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>선택된 전략 인스턴스</CardTitle>
          <CardDescription>전역 선택 상태를 확인하는 중입니다.</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          인스턴스 정보를 불러오는 중...
        </CardContent>
      </Card>
    );
  }

  if (!instanceMeta) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>선택된 전략 인스턴스</CardTitle>
          <CardDescription>
            선택 상태는 유지되지만, 현재 화면에서 인스턴스 메타데이터를 찾지
            못했습니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          선택 ID: {selectedInstanceId ?? "전체"}
        </CardContent>
      </Card>
    );
  }

  const watchlistValue =
    "watchlistCount" in instanceMeta
      ? instanceMeta.watchlistCount === null
        ? "확인 불가"
        : String(instanceMeta.watchlistCount)
      : "상세 API 범위 아님";

  const brokerAccountValue =
    "brokerAccountMasked" in instanceMeta
      ? (instanceMeta.brokerAccountMasked ?? "연결 계좌 없음")
      : "상세 로딩 후 표시";

  const budgetValue =
    "budgetAmount" in instanceMeta
      ? `${instanceMeta.budgetAmount.toLocaleString("ko-KR")}원`
      : "상세 로딩 후 표시";

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <span className="truncate">{instanceMeta.name}</span>
          <Badge variant="secondary">
            {instanceMeta.executionMode.toUpperCase()}
          </Badge>
          {instanceMeta.autoPausedReason && (
            <Badge variant="destructive">AUTO-PAUSED</Badge>
          )}
        </CardTitle>
        <CardDescription>
          선택된 인스턴스 기준 실제 대시보드 상세를 표시합니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
        <DashboardSelectionRow label="인스턴스 ID" value={instanceMeta.id} />
        <DashboardSelectionRow
          label="상태"
          value={instanceMeta.lifecycleState}
        />
        <DashboardSelectionRow
          label="모드"
          value={instanceMeta.executionMode}
        />
        <DashboardSelectionRow
          label="전략 예산"
          value={budgetValue}
        />
        <DashboardSelectionRow
          label="연결 계좌"
          value={brokerAccountValue}
        />
        <DashboardSelectionRow
          label="감시 종목 수"
          value={watchlistValue}
        />
      </CardContent>
    </Card>
  );
}

function DashboardSelectionRow({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border bg-muted/20 p-3">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="font-medium break-all">{value}</span>
    </div>
  );
}
