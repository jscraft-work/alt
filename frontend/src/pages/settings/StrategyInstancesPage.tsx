import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Copy,
  History,
  ListPlus,
  Loader2,
  MoreHorizontal,
  Pencil,
  Pause,
  Play,
  Plus,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import SettingsHeader from "@/components/settings/SettingsHeader";
import StrategyInstanceDialog from "@/components/settings/StrategyInstanceDialog";
import DuplicateInstanceDialog from "@/components/settings/DuplicateInstanceDialog";
import { useStrategyInstances, useTransitionStrategyInstance } from "@/hooks/use-strategy-instances";
import { formatKrw } from "@/lib/format";
import type { LifecycleState, StrategyInstance } from "@/lib/api-types";

/**
 * 전략 인스턴스 관리 — docs/spec2.md §7.6 / §10.2, docs/04-api-spec.md §8.4~8.8.
 */
export default function StrategyInstancesPage() {
  const { data, isLoading, error } = useStrategyInstances();
  const transition = useTransitionStrategyInstance();

  const [editDialog, setEditDialog] = useState<
    | { mode: "create" }
    | { mode: "edit"; instance: StrategyInstance }
    | null
  >(null);
  const [duplicateDialog, setDuplicateDialog] =
    useState<StrategyInstance | null>(null);

  const onTransition = (
    instance: StrategyInstance,
    targetState: LifecycleState,
  ) => {
    transition.mutate(
      {
        id: instance.id,
        body: { targetState, version: instance.version },
      },
      {
        onSuccess: () => {
          toast.success(`상태가 ${targetState} 로 전환되었습니다.`);
        },
      },
    );
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="전략 인스턴스"
        description="실행 단위 인스턴스의 설정, 상태 전환, 복제를 관리합니다."
        action={
          <Button onClick={() => setEditDialog({ mode: "create" })}>
            <Plus className="size-4" /> 추가
          </Button>
        }
      />

      {isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중...
        </div>
      ) : error ? (
        <p className="text-sm text-destructive">{error.message}</p>
      ) : !data || data.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          등록된 전략 인스턴스가 없습니다.
        </p>
      ) : (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>이름</TableHead>
                <TableHead>모드</TableHead>
                <TableHead>상태</TableHead>
                <TableHead className="text-right">예산</TableHead>
                <TableHead>계좌</TableHead>
                <TableHead className="w-[1%]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{row.name}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">
                      {row.executionMode.toUpperCase()}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <LifecycleBadge state={row.lifecycleState} />
                      {row.autoPausedReason && (
                        <Badge variant="destructive">AUTO-PAUSED</Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatKrw(row.budgetAmount)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {row.brokerAccountId ?? "-"}
                  </TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        render={
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label="동작 메뉴"
                          >
                            <MoreHorizontal className="size-4" />
                          </Button>
                        }
                      />
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem
                          onClick={() =>
                            setEditDialog({ mode: "edit", instance: row })
                          }
                        >
                          <Pencil className="size-4" /> 수정
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          render={
                            <Link
                              to={`/settings/instances/${row.id}/prompt-versions`}
                            />
                          }
                        >
                          <History className="size-4" /> 프롬프트 버전
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          render={
                            <Link
                              to={`/settings/instances/${row.id}/watchlist`}
                            />
                          }
                        >
                          <ListPlus className="size-4" /> 감시 종목
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => setDuplicateDialog(row)}
                        >
                          <Copy className="size-4" /> 복제
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        {row.lifecycleState !== "active" && (
                          <DropdownMenuItem
                            onClick={() => onTransition(row, "active")}
                            disabled={transition.isPending}
                          >
                            <Play className="size-4" /> 활성화
                          </DropdownMenuItem>
                        )}
                        {row.lifecycleState !== "inactive" && (
                          <DropdownMenuItem
                            onClick={() => onTransition(row, "inactive")}
                            disabled={transition.isPending}
                          >
                            <Pause className="size-4" /> 비활성화
                          </DropdownMenuItem>
                        )}
                        {row.lifecycleState !== "draft" && (
                          <DropdownMenuItem
                            onClick={() => onTransition(row, "draft")}
                            disabled={transition.isPending}
                          >
                            <Pencil className="size-4" /> draft 로 전환
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <StrategyInstanceDialog
        key={
          editDialog
            ? editDialog.mode === "edit"
              ? editDialog.instance.id
              : "create"
            : "closed"
        }
        open={editDialog !== null}
        mode={editDialog?.mode ?? "create"}
        instance={editDialog?.mode === "edit" ? editDialog.instance : undefined}
        onOpenChange={(open) => {
          if (!open) setEditDialog(null);
        }}
        onSuccess={(action) => {
          toast.success(
            action === "create"
              ? "인스턴스가 생성되었습니다."
              : "인스턴스가 수정되었습니다.",
          );
          setEditDialog(null);
        }}
      />

      <DuplicateInstanceDialog
        open={duplicateDialog !== null}
        source={duplicateDialog}
        onOpenChange={(open) => {
          if (!open) setDuplicateDialog(null);
        }}
        onSuccess={() => {
          toast.success("인스턴스가 복제되었습니다. (draft 상태)");
          setDuplicateDialog(null);
        }}
      />
    </div>
  );
}

function LifecycleBadge({ state }: { state: LifecycleState }) {
  const variant: "secondary" | "default" | "outline" =
    state === "active" ? "default" : state === "draft" ? "outline" : "secondary";
  return <Badge variant={variant}>{state}</Badge>;
}
