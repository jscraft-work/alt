import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";
import { useSelectableStrategyInstances } from "@/hooks/use-strategy-instances";
import { cn } from "@/lib/utils";

const ALL_INSTANCES_VALUE = "__all__";

export default function StrategyInstanceSelector() {
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

  return (
    <div className="flex min-w-0 items-center gap-2">
      <div className="hidden min-w-0 sm:flex sm:flex-col">
        <span className="text-[11px] font-medium text-muted-foreground">
          전략 인스턴스
        </span>
        <span className="text-[11px] text-muted-foreground/80">
          전체 또는 개별 보기
        </span>
      </div>

      <div className="relative min-w-0">
        <select
          className={cn(
            "h-9 w-[180px] rounded-lg border border-input bg-background px-3 pr-9 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 sm:w-[240px]",
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
          <option value={ALL_INSTANCES_VALUE}>전체 전략</option>
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
          목록 조회 실패, 전체 보기만 사용 가능합니다.
        </p>
      )}
    </div>
  );
}
