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
import { useCreatePromptVersion } from "@/hooks/use-prompt-versions";

/**
 * 전략 인스턴스 프롬프트 버전 생성 다이얼로그 — docs/04-api-spec.md §8.10.
 */

const schema = z.object({
  promptText: z
    .string()
    .refine((value) => value.trim().length > 0, "프롬프트를 입력해 주세요."),
  changeNote: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

interface PromptVersionDialogProps {
  instanceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export default function PromptVersionDialog({
  instanceId,
  open,
  onOpenChange,
  onSuccess,
}: PromptVersionDialogProps) {
  const create = useCreatePromptVersion(instanceId);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      promptText: "",
      changeNote: "",
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        promptText: "",
        changeNote: "",
      });
    }
  }, [open, reset]);

  const onSubmit = handleSubmit(async (values) => {
    await create.mutateAsync({
      instanceId,
      body: {
        promptText: values.promptText.trim(),
        changeNote: values.changeNote?.trim() || null,
      },
    });
    onSuccess();
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>새 프롬프트 버전 생성</DialogTitle>
          <DialogDescription>
            저장 즉시 버전 이력에 추가되고, 새 버전이 현재 적용본으로 설정됩니다.
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="prompt-version-note"
            label="변경 메모"
            helpText="선택 사항. 무엇을 바꿨는지 간단히 남겨 두면 복원 시 구분이 쉽습니다."
            error={errors.changeNote?.message}
          >
            <Input
              id="prompt-version-note"
              placeholder="예: 뉴스 요약 강조, 리스크 제한 문구 보강"
              {...register("changeNote")}
            />
          </FormField>

          <FormField
            id="prompt-version-text"
            label="프롬프트"
            required
            error={errors.promptText?.message}
          >
            <Textarea
              id="prompt-version-text"
              className="min-h-[320px] font-mono text-xs"
              placeholder="전략 판단에 사용할 프롬프트를 입력해 주세요."
              {...register("promptText")}
            />
          </FormField>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              취소
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : null}
              저장
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
