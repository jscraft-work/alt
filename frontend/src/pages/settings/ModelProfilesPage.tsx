import { useState } from "react";
import { Loader2, Pencil, Plus } from "lucide-react";
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
import SettingsHeader from "@/components/settings/SettingsHeader";
import ModelProfileDialog from "@/components/settings/ModelProfileDialog";
import { useModelProfiles } from "@/hooks/use-model-profiles";
import { formatKstDateTime } from "@/lib/format";
import type { ModelProfile } from "@/lib/api-types";

/**
 * 모델 프로필 — docs/04-api-spec.md §8.15~8.16, spec2.md §7.6 (모델 설정).
 */
export default function ModelProfilesPage() {
  const { data, isLoading, error } = useModelProfiles();
  const [dialog, setDialog] = useState<
    | { mode: "create" }
    | { mode: "edit"; profile: ModelProfile }
    | null
  >(null);

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title="모델 프로필"
        description="트레이딩/리포트/뉴스에 사용할 LLM 모델 프로필을 관리합니다."
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
          등록된 모델 프로필이 없습니다.
        </p>
      ) : (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>이름</TableHead>
                <TableHead>용도</TableHead>
                <TableHead>제공자</TableHead>
                <TableHead>모델</TableHead>
                <TableHead>활성</TableHead>
                <TableHead>수정일</TableHead>
                <TableHead className="w-[1%]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{row.name}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{row.purpose}</Badge>
                  </TableCell>
                  <TableCell>{row.provider}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {row.modelName}
                  </TableCell>
                  <TableCell>
                    {row.enabled ? (
                      <Badge>ON</Badge>
                    ) : (
                      <Badge variant="secondary">OFF</Badge>
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
                      onClick={() =>
                        setDialog({ mode: "edit", profile: row })
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

      <ModelProfileDialog
        key={dialog ? (dialog.mode === "edit" ? dialog.profile.id : "create") : "closed"}
        open={dialog !== null}
        mode={dialog?.mode ?? "create"}
        profile={dialog?.mode === "edit" ? dialog.profile : undefined}
        onOpenChange={(open) => {
          if (!open) setDialog(null);
        }}
        onSuccess={(action) => {
          toast.success(
            action === "create"
              ? "모델 프로필이 추가되었습니다."
              : "모델 프로필이 수정되었습니다.",
          );
          setDialog(null);
        }}
      />
    </div>
  );
}
