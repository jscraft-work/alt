import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, History, Loader2, Plus } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import SettingsHeader from "@/components/settings/SettingsHeader";
import PromptVersionDialog from "@/components/settings/PromptVersionDialog";
import {
  useActivatePromptVersion,
  usePromptVersions,
} from "@/hooks/use-prompt-versions";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";
import { formatKstDateTime } from "@/lib/format";
import type { PromptVersion } from "@/lib/api-types";

/**
 * 인스턴스 프롬프트 버전 관리 화면 — docs/04-api-spec.md §8.9~8.11.
 */

export default function PromptVersionsPage() {
  const params = useParams();
  const instanceId = params.id ?? "";
  const instances = useStrategyInstances();
  const instance = instances.data?.find((row) => row.id === instanceId);

  const promptVersions = usePromptVersions(instanceId);
  const activate = useActivatePromptVersion(instanceId);

  const [createOpen, setCreateOpen] = useState(false);
  const [pendingActivateId, setPendingActivateId] = useState<string | null>(
    null,
  );

  const onActivate = async (promptVersion: PromptVersion) => {
    if (!instance) {
      toast.error("인스턴스 정보를 아직 불러오지 못했습니다.");
      return;
    }

    setPendingActivateId(promptVersion.id);
    try {
      await activate.mutateAsync({
        instanceId,
        promptVersionId: promptVersion.id,
        body: { version: instance.version },
      });
      toast.success(
        `v${promptVersion.versionNo} 내용을 복원해 새 현재 버전을 생성했습니다.`,
      );
    } finally {
      setPendingActivateId(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title={`프롬프트 버전${instance ? ` · ${instance.name}` : ""}`}
        description="전략 인스턴스별 프롬프트 이력을 조회하고, 새 버전을 추가하거나 이전 버전을 복원해 현재 버전으로 다시 적용합니다."
        action={
          <>
            <Button variant="outline" render={<Link to="/settings/instances" />}>
              <ArrowLeft className="size-4" /> 인스턴스 목록
            </Button>
            <Button
              variant="outline"
              render={<Link to={`/settings/instances/${instanceId}/watchlist`} />}
            >
              감시 종목
            </Button>
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="size-4" /> 새 버전
            </Button>
          </>
        }
      />

      {instances.error ? (
        <p className="text-sm text-destructive">{instances.error.message}</p>
      ) : null}

      {promptVersions.isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> 불러오는 중...
        </div>
      ) : promptVersions.error ? (
        <p className="text-sm text-destructive">
          {promptVersions.error.message}
        </p>
      ) : !promptVersions.data || promptVersions.data.length === 0 ? (
        <Card className="py-0">
          <CardHeader>
            <CardTitle>프롬프트 버전이 없습니다.</CardTitle>
            <CardDescription>
              첫 프롬프트 버전을 생성하면 여기에서 이력을 관리할 수 있습니다.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : (
        <div className="flex flex-col gap-4">
          {promptVersions.data.map((version) => (
            <Card
              key={version.id}
              className={version.current ? "ring-2 ring-primary/20" : undefined}
            >
              <CardHeader className="border-b">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex flex-col gap-1">
                    <CardTitle className="flex items-center gap-2">
                      <span>v{version.versionNo}</span>
                      {version.current ? <Badge>현재 적용 중</Badge> : null}
                    </CardTitle>
                    <CardDescription>
                      생성 {formatKstDateTime(version.createdAt)}
                      {version.createdBy ? ` · 작성자 ${version.createdBy}` : ""}
                    </CardDescription>
                  </div>
                  <Button
                    variant={version.current ? "secondary" : "outline"}
                    disabled={
                      version.current ||
                      !instance ||
                      activate.isPending ||
                      pendingActivateId === version.id
                    }
                    onClick={() => {
                      void onActivate(version);
                    }}
                  >
                    {pendingActivateId === version.id ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <History className="size-4" />
                    )}
                    {version.current ? "사용 중" : "이 버전으로 복원"}
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <div className="flex flex-col gap-1">
                  <p className="text-xs font-medium text-muted-foreground">
                    변경 메모
                  </p>
                  <p className="text-sm">
                    {version.changeNote?.trim() || "메모가 없습니다."}
                  </p>
                </div>
                <div className="flex flex-col gap-1">
                  <p className="text-xs font-medium text-muted-foreground">
                    프롬프트 본문
                  </p>
                  <pre className="overflow-x-auto rounded-lg bg-muted/50 p-3 font-mono text-xs whitespace-pre-wrap break-words">
                    {version.promptText}
                  </pre>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <PromptVersionDialog
        instanceId={instanceId}
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={() => {
          toast.success("새 프롬프트 버전이 생성되었습니다.");
          setCreateOpen(false);
        }}
      />
    </div>
  );
}
