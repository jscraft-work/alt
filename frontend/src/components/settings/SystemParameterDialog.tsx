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
  useCreateSystemParameter,
  useUpdateSystemParameter,
} from "@/hooks/use-system-parameters";
import type { SystemParameter } from "@/lib/api-types";

const schema = z.object({
  parameterKey: z
    .string()
    .min(1, "키를 입력해 주세요.")
    .max(120, "키는 120자 이하여야 합니다."),
  valueText: z
    .string()
    .min(1, "값(JSON)을 입력해 주세요.")
    .refine((value) => {
      try {
        JSON.parse(value);
        return true;
      } catch {
        return false;
      }
    }, "올바른 JSON 형식이 아닙니다."),
  description: z.string().max(500, "설명은 500자 이하여야 합니다.").optional(),
});

type FormValues = z.infer<typeof schema>;

interface SystemParameterDialogProps {
  open: boolean;
  mode: "create" | "edit";
  parameter?: SystemParameter;
  onOpenChange: (open: boolean) => void;
  onSuccess: (action: "create" | "edit") => void;
}

function stringifyJsonValue(value: unknown): string {
  return JSON.stringify(value, null, 2) ?? "null";
}

export default function SystemParameterDialog({
  open,
  mode,
  parameter,
  onOpenChange,
  onSuccess,
}: SystemParameterDialogProps) {
  const create = useCreateSystemParameter();
  const update = useUpdateSystemParameter();
  const isSaving = create.isPending || update.isPending;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      parameterKey: parameter?.parameterKey ?? "",
      valueText: stringifyJsonValue(parameter?.valueJson ?? {}),
      description: parameter?.description ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        parameterKey: parameter?.parameterKey ?? "",
        valueText: stringifyJsonValue(parameter?.valueJson ?? {}),
        description: parameter?.description ?? "",
      });
    }
  }, [open, parameter, reset]);

  const onSubmit = handleSubmit(async (values) => {
    const payload = {
      valueJson: JSON.parse(values.valueText),
      description: values.description?.trim() || null,
    };

    if (mode === "create") {
      await create.mutateAsync({
        parameterKey: values.parameterKey.trim(),
        ...payload,
      });
      onSuccess("create");
      return;
    }

    if (parameter) {
      await update.mutateAsync({
        parameterKey: parameter.parameterKey,
        body: {
          ...payload,
          version: parameter.version,
        },
      });
      onSuccess("edit");
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? "시스템 파라미터 추가" : "시스템 파라미터 수정"}
          </DialogTitle>
          <DialogDescription>
            글로벌 운영 설정 값을 JSON 텍스트로 안전하게 관리합니다.
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="system-parameter-key"
            label="키"
            required
            helpText="예: market.snapshot.poll.seconds"
            error={errors.parameterKey?.message}
          >
            <Input
              id="system-parameter-key"
              disabled={mode === "edit" || isSaving}
              {...register("parameterKey")}
            />
          </FormField>

          <FormField
            id="system-parameter-description"
            label="설명"
            helpText="운영자가 파라미터 용도를 빠르게 이해할 수 있도록 남깁니다."
            error={errors.description?.message}
          >
            <Input
              id="system-parameter-description"
              disabled={isSaving}
              {...register("description")}
            />
          </FormField>

          <FormField
            id="system-parameter-value"
            label="값 (JSON)"
            required
            helpText='문자열은 `"text"`, 숫자는 `123`, 불리언은 `true`, 객체는 `{ "a": 1 }` 형식으로 입력합니다.'
            error={errors.valueText?.message}
          >
            <Textarea
              id="system-parameter-value"
              className="min-h-[220px] font-mono text-xs"
              disabled={isSaving}
              placeholder='{ "enabled": true }'
              {...register("valueText")}
            />
          </FormField>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={isSaving}
              onClick={() => onOpenChange(false)}
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
