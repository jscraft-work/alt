import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";
import { useSelectableStrategyInstances } from "@/hooks/use-strategy-instances";
import { cn } from "@/lib/utils";

const ALL_INSTANCES_VALUE = "__all__";

interface StrategyInstanceSelectorProps {
  /**
   * 상단 작은 라벨. 기본값 "전략 인스턴스".
   */
  label?: string;
  /**
   * 라벨 아래 보조 텍스트. 기본값 "전체 또는 개별 보기".
   */
  hint?: string;
  /**
   * "전체 전략" (`null`) 옵션 표시 여부. 인스턴스 단위 데이터를 보여주는 페이지에서는
   * 의미가 없으므로 false 로 줄 수 있다. 기본값 true.
   */
  allowAll?: boolean;
  /**
   * 가로 폭. tailwind 클래스. 기본 `w-[180px] sm:w-[240px]`.
   */
  className?: string;
}

/**
 * F6 — 글로벌 헤더 selector 에서 페이지 내부 selector 로 역할 이동.
 *
 * StrategyInstanceSelectionProvider 의 selectedInstanceId 를 읽고 쓴다.
 * URL `?instanceId=...` 동기화는 페이지에서 `usePageInstanceSync()` 로 별도 처리.
 */
export default function StrategyInstanceSelector({
  label = "전략 인스턴스",
  hint = "전체 또는 개별 보기",
  allowAll = true,
  className,
}: StrategyInstanceSelectorProps = {}) {
  const { data, isLoading, error } = useSelectableStrategyInstances();
  const { selectedInstanceId, setSelectedInstanceId } =
    useStrategyInstanceSelection();

  useEffect(() => {
    if (!selectedInstanceId || !data) {
      return;
    }

    const hasSelectedInstance = data.some(
      (instance) => instance.id === selectedInstanceId,
    );

    if (!hasSelectedInstance) {
      setSelectedInstanceId(null);
    }
  }, [data, selectedInstanceId, setSelectedInstanceId]);

  // 인스턴스 단위 페이지에서 allowAll=false 인 경우, "전체" 선택을 방지하고
  // 첫 인스턴스로 자동 선택.
  useEffect(() => {
    if (allowAll) return;
    if (!data || data.length === 0) return;
    if (!selectedInstanceId) {
      setSelectedInstanceId(data[0].id);
    }
  }, [allowAll, data, selectedInstanceId, setSelectedInstanceId]);

  return (
    <div className="flex min-w-0 items-center gap-2">
      <div className="hidden min-w-0 sm:flex sm:flex-col">
        <span className="text-[11px] font-medium text-muted-foreground">
          {label}
        </span>
        <span className="text-[11px] text-muted-foreground/80">{hint}</span>
      </div>

      <div className="relative min-w-0">
        <select
          className={cn(
            "h-9 rounded-lg border border-input bg-background px-3 pr-9 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
            className ?? "w-[180px] sm:w-[240px]",
            isLoading && "text-muted-foreground",
          )}
          aria-label="전략 인스턴스 선택"
          value={selectedInstanceId ?? ALL_INSTANCES_VALUE}
          onChange={(event) => {
            const nextValue = event.target.value;
            setSelectedInstanceId(
              nextValue === ALL_INSTANCES_VALUE ? null : nextValue,
            );
          }}
          disabled={isLoading}
        >
          {allowAll && (
            <option value={ALL_INSTANCES_VALUE}>전체 전략</option>
          )}
          {(data ?? []).map((instance) => (
            <option key={instance.id} value={instance.id}>
              {instance.name}
            </option>
          ))}
        </select>
        {isLoading && (
          <Loader2 className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin text-muted-foreground" />
        )}
      </div>

      {error && (
        <p className="hidden text-xs text-destructive lg:block">
          목록 조회 실패{allowAll ? ", 전체 보기만 사용 가능합니다." : "."}
        </p>
      )}
    </div>
  );
}
