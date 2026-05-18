import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import FormField from "@/components/settings/FormField";
import {
  useCreateStrategyTemplate,
  useUpdateStrategyTemplate,
} from "@/hooks/use-strategy-templates";
import { parseJsonRecord, stringifyJsonRecord } from "@/lib/json";
import type { StrategyTemplate } from "@/lib/api-types";

/**
 * 전략 템플릿 생성/수정 다이얼로그 — docs/spec2.md §7.6, §8.2/§8.3.
 */

const schema = z.object({
  name: z.string().min(1, "이름을 입력해 주세요.").max(80),
  description: z.string().max(400).optional(),
  defaultCycleMinutes: z
    .number({ message: "숫자를 입력해 주세요." })
    .int("정수만 허용됩니다.")
    .min(1, "1분 이상이어야 합니다.")
    .max(30, "30분 이하만 허용됩니다."),
  defaultPromptText: z.string().optional(),
  defaultExecutionConfigJson: z
    .string()
    .optional()
    .refine((value) => {
      if (!value || value.trim() === "") return true;
      try {
        parseJsonRecord(value);
        return true;
      } catch {
        return false;
      }
    }, "올바른 JSON 객체가 아닙니다."),
  defaultTradingModelProfileId: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

interface StrategyTemplateDialogProps {
  open: boolean;
  mode: "create" | "edit";
  template?: StrategyTemplate;
  onOpenChange: (open: boolean) => void;
  onSuccess: (action: "create" | "edit") => void;
}

export default function StrategyTemplateDialog({
  open,
  mode,
  template,
  onOpenChange,
  onSuccess,
}: StrategyTemplateDialogProps) {
  const create = useCreateStrategyTemplate();
  const update = useUpdateStrategyTemplate();
  const isSaving = create.isPending || update.isPending;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: template?.name ?? "",
      description: template?.description ?? "",
      defaultCycleMinutes: template?.defaultCycleMinutes ?? 5,
      defaultPromptText: template?.defaultPromptText ?? "",
      defaultExecutionConfigJson: stringifyJsonRecord(
        template?.defaultExecutionConfig,
      ),
      defaultTradingModelProfileId:
        template?.defaultTradingModelProfileId ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        name: template?.name ?? "",
        description: template?.description ?? "",
        defaultCycleMinutes: template?.defaultCycleMinutes ?? 5,
        defaultPromptText: template?.defaultPromptText ?? "",
        defaultExecutionConfigJson: stringifyJsonRecord(
          template?.defaultExecutionConfig,
        ),
        defaultTradingModelProfileId:
          template?.defaultTradingModelProfileId ?? "",
      });
    }
  }, [open, template, reset]);

  const onSubmit = handleSubmit(async (values) => {
    const executionConfig = values.defaultExecutionConfigJson
      ? parseJsonRecord(values.defaultExecutionConfigJson)
      : undefined;
    const profileId =
      values.defaultTradingModelProfileId &&
      values.defaultTradingModelProfileId.trim() !== ""
        ? values.defaultTradingModelProfileId.trim()
        : null;

    if (mode === "create") {
      await create.mutateAsync({
        name: values.name.trim(),
        description: values.description?.trim() || null,
        defaultCycleMinutes: values.defaultCycleMinutes,
        defaultPromptText: values.defaultPromptText || undefined,
        defaultExecutionConfig: executionConfig,
        defaultTradingModelProfileId: profileId,
      });
      onSuccess("create");
    } else if (template) {
      await update.mutateAsync({
        id: template.id,
        body: {
          name: values.name.trim(),
          description: values.description?.trim() || null,
          defaultCycleMinutes: values.defaultCycleMinutes,
          defaultPromptText: values.defaultPromptText || undefined,
          defaultExecutionConfig: executionConfig,
          defaultTradingModelProfileId: profileId,
          version: template.version,
        },
      });
      onSuccess("edit");
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] max-w-2xl flex-col overflow-hidden p-0">
        <DialogHeader>
          <div className="border-b px-6 py-5">
            <DialogTitle>
              {mode === "create" ? "전략 템플릿 추가" : "전략 템플릿 수정"}
            </DialogTitle>
            <DialogDescription>
              신규 인스턴스의 기본값으로 사용됩니다. (저장 후 변경은 신규 인스턴스에만 적용)
            </DialogDescription>
          </div>
        </DialogHeader>

        <form
          className="flex min-h-0 flex-1 flex-col"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
            <div className="flex flex-col gap-4">
              <FormField
                id="template-name"
                label="이름"
                required
                error={errors.name?.message}
              >
                <Input id="template-name" {...register("name")} />
              </FormField>

              <FormField
                id="template-description"
                label="설명"
                error={errors.description?.message}
              >
                <Input id="template-description" {...register("description")} />
              </FormField>

              <FormField
                id="template-cycle"
                label="기본 실행 주기 (분)"
                required
                helpText="1 ~ 30 분, 권장 5분"
                error={errors.defaultCycleMinutes?.message}
              >
                <Input
                  id="template-cycle"
                  type="number"
                  min={1}
                  max={30}
                  {...register("defaultCycleMinutes", { valueAsNumber: true })}
                />
              </FormField>

              <FormField
                id="template-profile"
                label="기본 트레이딩 모델 프로필 ID"
                helpText="UUID. 비워두면 인스턴스 단위에서 지정합니다."
                error={errors.defaultTradingModelProfileId?.message}
              >
                <Input
                  id="template-profile"
                  {...register("defaultTradingModelProfileId")}
                  placeholder="uuid"
                />
              </FormField>

              <FormField
                id="template-prompt"
                label="기본 프롬프트"
                error={errors.defaultPromptText?.message}
              >
                <Textarea
                  id="template-prompt"
                  className="min-h-[100px] font-mono text-xs"
                  {...register("defaultPromptText")}
                />
              </FormField>

              <FormField
                id="template-exec-config"
                label="기본 실행 설정 (JSON)"
                error={errors.defaultExecutionConfigJson?.message}
              >
                <Textarea
                  id="template-exec-config"
                  className="min-h-[100px] font-mono text-xs"
                  placeholder='{ "dailyCostCapKrw": 5000 }'
                  {...register("defaultExecutionConfigJson")}
                />
              </FormField>
            </div>
          </div>

          <DialogFooter className="border-t px-6 py-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSaving}
            >
              취소
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving && <Loader2 className="size-4 animate-spin" />}
              저장
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
