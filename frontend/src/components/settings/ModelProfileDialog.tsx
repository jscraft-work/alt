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
  useCreateModelProfile,
  useUpdateModelProfile,
} from "@/hooks/use-model-profiles";
import { parseJsonRecord, stringifyJsonRecord } from "@/lib/json";
import type { ModelProfile } from "@/lib/api-types";

/**
 * 모델 프로필 생성/수정 — docs/04-api-spec.md §8.16.
 */

const PURPOSES = ["trading", "report", "news"] as const;

const schema = z.object({
  name: z.string().min(1, "이름을 입력해 주세요.").max(80),
  purpose: z.enum(PURPOSES),
  provider: z.string().min(1, "제공자를 입력해 주세요."),
  modelName: z.string().min(1, "모델명을 입력해 주세요."),
  enabled: z.boolean(),
  parametersJson: z
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
});
type FormValues = z.infer<typeof schema>;

interface ModelProfileDialogProps {
  open: boolean;
  mode: "create" | "edit";
  profile?: ModelProfile;
  onOpenChange: (open: boolean) => void;
  onSuccess: (action: "create" | "edit") => void;
}

export default function ModelProfileDialog({
  open,
  mode,
  profile,
  onOpenChange,
  onSuccess,
}: ModelProfileDialogProps) {
  const create = useCreateModelProfile();
  const update = useUpdateModelProfile();
  const isSaving = create.isPending || update.isPending;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: profile?.name ?? "",
      purpose: (profile?.purpose as (typeof PURPOSES)[number]) ?? "trading",
      provider: profile?.provider ?? "",
      modelName: profile?.modelName ?? "",
      enabled: profile?.enabled ?? true,
      parametersJson: stringifyJsonRecord(profile?.parameters),
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        name: profile?.name ?? "",
        purpose: (profile?.purpose as (typeof PURPOSES)[number]) ?? "trading",
        provider: profile?.provider ?? "",
        modelName: profile?.modelName ?? "",
        enabled: profile?.enabled ?? true,
        parametersJson: stringifyJsonRecord(profile?.parameters),
      });
    }
  }, [open, profile, reset]);

  const onSubmit = handleSubmit(async (values) => {
    const parameters = values.parametersJson
      ? parseJsonRecord(values.parametersJson)
      : undefined;

    if (mode === "create") {
      await create.mutateAsync({
        name: values.name.trim(),
        purpose: values.purpose,
        provider: values.provider.trim(),
        modelName: values.modelName.trim(),
        enabled: values.enabled,
        parameters,
      });
      onSuccess("create");
    } else if (profile) {
      await update.mutateAsync({
        id: profile.id,
        body: {
          name: values.name.trim(),
          purpose: values.purpose,
          provider: values.provider.trim(),
          modelName: values.modelName.trim(),
          enabled: values.enabled,
          parameters,
          version: profile.version,
        },
      });
      onSuccess("edit");
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? "모델 프로필 추가" : "모델 프로필 수정"}
          </DialogTitle>
          <DialogDescription>
            트레이딩 / 리포트 / 뉴스 용도로 사용할 모델 호출 파라미터를 관리합니다.
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="model-name"
            label="이름"
            required
            error={errors.name?.message}
          >
            <Input id="model-name" {...register("name")} />
          </FormField>

          <FormField
            id="model-purpose"
            label="용도"
            required
            error={errors.purpose?.message}
          >
            <select
              id="model-purpose"
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              {...register("purpose")}
            >
              {PURPOSES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </FormField>

          <FormField
            id="model-provider"
            label="제공자"
            required
            helpText="예: openai, anthropic, google"
            error={errors.provider?.message}
          >
            <Input id="model-provider" {...register("provider")} />
          </FormField>

          <FormField
            id="model-modelName"
            label="모델명"
            required
            helpText="예: gpt-4o, claude-opus-4-5"
            error={errors.modelName?.message}
          >
            <Input id="model-modelName" {...register("modelName")} />
          </FormField>

          <div className="flex items-center gap-2">
            <input
              id="model-enabled"
              type="checkbox"
              className="size-4"
              {...register("enabled")}
            />
            <label htmlFor="model-enabled" className="text-sm">
              활성화
            </label>
          </div>

          <FormField
            id="model-parameters"
            label="호출 파라미터 (JSON)"
            helpText="temperature, top_p, max_tokens 등."
            error={errors.parametersJson?.message}
          >
            <Textarea
              id="model-parameters"
              className="min-h-[100px] font-mono text-xs"
              placeholder='{ "temperature": 0.2 }'
              {...register("parametersJson")}
            />
          </FormField>

          <DialogFooter>
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
