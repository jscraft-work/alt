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
import {
  useCreateBrokerAccount,
  useUpdateBrokerAccount,
} from "@/hooks/use-broker-accounts";
import type { BrokerAccount } from "@/lib/api-types";

/**
 * 브로커 계좌 생성/수정 — docs/04-api-spec.md §8.18.
 *
 * 보안 NFR (§10.8): API 키 / 시크릿은 서버에서 평문 저장하지 않으며,
 * 클라이언트는 입력값만 전송하고 응답에서는 마스킹된 형태만 받는다.
 */

const schema = z.object({
  alias: z.string().min(1, "별칭을 입력해 주세요.").max(80),
  brokerCode: z.string().min(1, "브로커 코드를 입력해 주세요."),
  accountNumber: z.string().optional(),
  apiKey: z.string().optional(),
  apiSecret: z.string().optional(),
  enabled: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

interface BrokerAccountDialogProps {
  open: boolean;
  mode: "create" | "edit";
  account?: BrokerAccount;
  onOpenChange: (open: boolean) => void;
  onSuccess: (action: "create" | "edit") => void;
}

export default function BrokerAccountDialog({
  open,
  mode,
  account,
  onOpenChange,
  onSuccess,
}: BrokerAccountDialogProps) {
  const create = useCreateBrokerAccount();
  const update = useUpdateBrokerAccount();
  const isSaving = create.isPending || update.isPending;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      alias: account?.alias ?? "",
      brokerCode: account?.brokerCode ?? "",
      accountNumber: "",
      apiKey: "",
      apiSecret: "",
      enabled: account?.enabled ?? true,
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        alias: account?.alias ?? "",
        brokerCode: account?.brokerCode ?? "",
        accountNumber: "",
        apiKey: "",
        apiSecret: "",
        enabled: account?.enabled ?? true,
      });
    }
  }, [open, account, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (mode === "create") {
      if (!values.accountNumber) {
        // 생성 시에는 계좌번호 필수
        return;
      }
      await create.mutateAsync({
        alias: values.alias.trim(),
        brokerCode: values.brokerCode.trim(),
        accountNumber: values.accountNumber.trim(),
        apiKey: values.apiKey || undefined,
        apiSecret: values.apiSecret || undefined,
        enabled: values.enabled,
      });
      onSuccess("create");
    } else if (account) {
      await update.mutateAsync({
        id: account.id,
        body: {
          alias: values.alias.trim(),
          brokerCode: values.brokerCode.trim(),
          accountNumber: values.accountNumber || undefined,
          apiKey: values.apiKey || undefined,
          apiSecret: values.apiSecret || undefined,
          enabled: values.enabled,
          version: account.version,
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
            {mode === "create" ? "브로커 계좌 추가" : "브로커 계좌 수정"}
          </DialogTitle>
          <DialogDescription>
            계좌번호와 API 키는 서버에 안전하게 저장되며 응답에서는 마스킹된 값만 제공됩니다.
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="broker-alias"
            label="별칭"
            required
            error={errors.alias?.message}
          >
            <Input id="broker-alias" {...register("alias")} />
          </FormField>

          <FormField
            id="broker-code"
            label="브로커 코드"
            required
            helpText="예: kis"
            error={errors.brokerCode?.message}
          >
            <Input id="broker-code" {...register("brokerCode")} />
          </FormField>

          {mode === "edit" && account && (
            <div className="rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
              현재 계좌번호 마스킹: {account.accountNumberMasked}
            </div>
          )}

          <FormField
            id="broker-account-number"
            label={mode === "create" ? "계좌번호" : "새 계좌번호 (선택)"}
            required={mode === "create"}
            helpText={
              mode === "edit"
                ? "비워두면 기존 값을 유지합니다."
                : "전체 계좌번호를 입력해 주세요."
            }
            error={errors.accountNumber?.message}
          >
            <Input
              id="broker-account-number"
              type="text"
              autoComplete="off"
              {...register("accountNumber")}
            />
          </FormField>

          <FormField
            id="broker-api-key"
            label="API 키 (선택)"
            helpText="저장 시 서버에서 암호화. 비워두면 기존 값을 유지합니다."
            error={errors.apiKey?.message}
          >
            <Input
              id="broker-api-key"
              type="password"
              autoComplete="new-password"
              {...register("apiKey")}
            />
          </FormField>

          <FormField
            id="broker-api-secret"
            label="API 시크릿 (선택)"
            error={errors.apiSecret?.message}
          >
            <Input
              id="broker-api-secret"
              type="password"
              autoComplete="new-password"
              {...register("apiSecret")}
            />
          </FormField>

          <div className="flex items-center gap-2">
            <input
              id="broker-enabled"
              type="checkbox"
              className="size-4"
              {...register("enabled")}
            />
            <label htmlFor="broker-enabled" className="text-sm">
              활성화
            </label>
          </div>

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
