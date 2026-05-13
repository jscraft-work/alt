import { useMemo, useState } from "react";
import { Loader2, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import DeleteSystemParameterDialog from "@/components/settings/DeleteSystemParameterDialog";
import SettingsHeader from "@/components/settings/SettingsHeader";
import SystemParameterDialog from "@/components/settings/SystemParameterDialog";
import {
  useDeleteSystemParameter,
  useSystemParameters,
} from "@/hooks/use-system-parameters";
import { formatKstDateTime } from "@/lib/format";
import type { SystemParameter } from "@/lib/api-types";

type DialogState =
  | { mode: "create" }
  | { mode: "edit"; parameter: SystemParameter }
  | null;

function inferValueType(value: unknown) {
  if (value === null) return "null";
  if (Array.isArray(value)) return "array";
  return typeof value;
}

function formatPreview(value: unknown): string {
  if (typeof value === "string") return value;
  return JSON.stringify(value) ?? "undefined";
}

function formatValueTypeLabel(value: unknown): string {
  switch (inferValueType(value)) {
    case "string":
      return "STRING";
    case "number":
      return "NUMBER";
    case "boolean":
      return "BOOLEAN";
    case "null":
      return "NULL";
    case "array":
      return "ARRAY";
    default:
      return "JSON";
  }
}

export default function SystemParametersPage() {
  const { data, isLoading, error } = useSystemParameters();
  const remove = useDeleteSystemParameter();
  const [dialog, setDialog] = useState<DialogState>(null);
  const [deleteTarget, setDeleteTarget] = useState<SystemParameter | null>(null);

  const rows = useMemo(() => data ?? [], [data]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    await remove.mutateAsync(deleteTarget.parameterKey);
    toast.success("시스템 파라미터가 삭제되었습니다.");
    setDeleteTarget(null);
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="시스템 파라미터"
        description="시스템 전체에 적용되는 글로벌 운영 설정 값을 관리합니다."
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
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          등록된 시스템 파라미터가 없습니다.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">
            총 {rows.length}건의 글로벌 운영 설정이 등록되어 있습니다.
          </p>

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>키</TableHead>
                  <TableHead>타입</TableHead>
                  <TableHead>값</TableHead>
                  <TableHead>설명</TableHead>
                  <TableHead className="text-right">버전</TableHead>
                  <TableHead>수정일</TableHead>
                  <TableHead className="w-[1%]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.parameterKey}>
                    <TableCell className="max-w-[220px] font-mono text-xs">
                      <div className="truncate" title={row.parameterKey}>
                        {row.parameterKey}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">
                        {formatValueTypeLabel(row.valueJson)}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-[280px] font-mono text-xs">
                      <div className="truncate" title={formatPreview(row.valueJson)}>
                        {formatPreview(row.valueJson)}
                      </div>
                    </TableCell>
                    <TableCell className="max-w-[260px]">
                      <div
                        className="truncate text-muted-foreground"
                        title={row.description ?? ""}
                      >
                        {row.description?.trim() ? row.description : "-"}
                      </div>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {row.version}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatKstDateTime(row.updatedAt)}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1">
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label="수정"
                          onClick={() =>
                            setDialog({ mode: "edit", parameter: row })
                          }
                        >
                          <Pencil className="size-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label="삭제"
                          onClick={() => setDeleteTarget(row)}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}

      <SystemParameterDialog
        key={
          dialog
            ? dialog.mode === "edit"
              ? dialog.parameter.parameterKey
              : "create"
            : "closed"
        }
        open={dialog !== null}
        mode={dialog?.mode ?? "create"}
        parameter={dialog?.mode === "edit" ? dialog.parameter : undefined}
        onOpenChange={(open) => {
          if (!open) setDialog(null);
        }}
        onSuccess={(action) => {
          toast.success(
            action === "create"
              ? "시스템 파라미터가 추가되었습니다."
              : "시스템 파라미터가 수정되었습니다.",
          );
          setDialog(null);
        }}
      />

      <DeleteSystemParameterDialog
        open={deleteTarget !== null}
        parameterKey={deleteTarget?.parameterKey}
        isDeleting={remove.isPending}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        onConfirm={() => {
          void handleDelete();
        }}
      />
    </div>
  );
}
