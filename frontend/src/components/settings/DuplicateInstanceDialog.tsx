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
import FormField from "@/components/settings/FormField";
import { useDuplicateStrategyInstance } from "@/hooks/use-strategy-instances";
import type { StrategyInstance } from "@/lib/api-types";

/**
 * 인스턴스 복제 다이얼로그 — docs/04-api-spec.md §8.8.
 * 결과는 항상 draft 상태이며 자산/판단 로그/주문 이력은 복제되지 않는다.
 */

const schema = z.object({
  name: z.string().min(1, "이름을 입력해 주세요.").max(80),
});
type FormValues = z.infer<typeof schema>;

interface DuplicateInstanceDialogProps {
  open: boolean;
  source: StrategyInstance | null;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export default function DuplicateInstanceDialog({
  open,
  source,
  onOpenChange,
  onSuccess,
}: DuplicateInstanceDialogProps) {
  const duplicate = useDuplicateStrategyInstance();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "" },
  });

  useEffect(() => {
    if (open && source) {
      reset({ name: `${source.name} (복사본)` });
    }
  }, [open, source, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!source) return;
    await duplicate.mutateAsync({
      id: source.id,
      body: { name: values.name.trim() },
    });
    onSuccess();
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>인스턴스 복제</DialogTitle>
          <DialogDescription>
            설정만 복제됩니다. 자산/보유/판단 로그/주문 이력은 복제되지 않으며 결과는 항상 draft 상태로 생성됩니다.
          </DialogDescription>
        </DialogHeader>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="duplicate-name"
            label="새 인스턴스 이름"
            required
            error={errors.name?.message}
          >
            <Input id="duplicate-name" {...register("name")} />
          </FormField>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={duplicate.isPending}
            >
              취소
            </Button>
            <Button type="submit" disabled={duplicate.isPending}>
              {duplicate.isPending && (
                <Loader2 className="size-4 animate-spin" />
              )}
              복제
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
