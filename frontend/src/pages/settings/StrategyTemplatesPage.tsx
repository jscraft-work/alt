import { useState } from "react";
import { Loader2, Pencil, Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import SettingsHeader from "@/components/settings/SettingsHeader";
import StrategyTemplateDialog from "@/components/settings/StrategyTemplateDialog";
import { useStrategyTemplates } from "@/hooks/use-strategy-templates";
import { formatKstDateTime } from "@/lib/format";
import type { StrategyTemplate } from "@/lib/api-types";

/**
 * 전략 템플릿 관리 (docs/spec2.md §7.6, docs/04-api-spec.md §8.1~8.3).
 */
export default function StrategyTemplatesPage() {
  const { data, isLoading, error } = useStrategyTemplates();
  const [dialog, setDialog] = useState<
    | { mode: "create" }
    | { mode: "edit"; template: StrategyTemplate }
    | null
  >(null);

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="전략 템플릿"
        description="신규 인스턴스 생성 시 기본값으로 사용될 템플릿을 관리합니다."
        action={
          <Button onClick={() => setDialog({ mode: "create" })}>
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
          등록된 전략 템플릿이 없습니다.
        </p>
      ) : (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>이름</TableHead>
                <TableHead>설명</TableHead>
                <TableHead className="text-right">기본 주기(분)</TableHead>
                <TableHead className="text-right">버전</TableHead>
                <TableHead>수정일</TableHead>
                <TableHead className="w-[1%]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{row.name}</TableCell>
                  <TableCell className="max-w-[280px] truncate text-muted-foreground">
                    {row.description ?? "-"}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {row.defaultCycleMinutes}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {row.version}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatKstDateTime(row.updatedAt)}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="수정"
                      onClick={() =>
                        setDialog({ mode: "edit", template: row })
                      }
                    >
                      <Pencil className="size-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <StrategyTemplateDialog
        key={dialog ? (dialog.mode === "edit" ? dialog.template.id : "create") : "closed"}
        open={dialog !== null}
        mode={dialog?.mode ?? "create"}
        template={dialog?.mode === "edit" ? dialog.template : undefined}
        onOpenChange={(open) => {
          if (!open) setDialog(null);
        }}
        onSuccess={(action) => {
          toast.success(
            action === "create" ? "템플릿이 생성되었습니다." : "템플릿이 수정되었습니다.",
          );
          setDialog(null);
        }}
      />
    </div>
  );
}
