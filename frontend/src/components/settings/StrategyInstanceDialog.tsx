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
  useCreateStrategyInstance,
  useUpdateStrategyInstance,
} from "@/hooks/use-strategy-instances";
import { useStrategyTemplates } from "@/hooks/use-strategy-templates";
import { useBrokerAccounts } from "@/hooks/use-broker-accounts";
import { useModelProfiles } from "@/hooks/use-model-profiles";
import { parseJsonRecord, stringifyJsonRecord } from "@/lib/json";
import type { ExecutionMode, StrategyInstance } from "@/lib/api-types";

/**
 * 전략 인스턴스 생성/수정 다이얼로그 — docs/04-api-spec.md §8.5/§8.6.
 *
 * 편집 가능 필드는 docs/spec2.md §10.2 의 lifecycle 제약을 따른다:
 *  - 인스턴스명, 실행 모드, 연결 계좌: `draft / inactive` 에서만 변경 가능
 *  - 예산: `draft` 에서만 변경 가능
 */

const EXECUTION_MODES: ExecutionMode[] = ["paper", "live"];

const schema = z.object({
  strategyTemplateId: z.string().min(1, "템플릿을 선택해 주세요."),
  name: z.string().min(1, "이름을 입력해 주세요.").max(80),
  executionMode: z.enum(["paper", "live"]),
  brokerAccountId: z.string().optional(),
  budgetAmount: z
    .number({ message: "숫자를 입력해 주세요." })
    .int()
    .min(1, "0보다 커야 합니다."),
  tradingModelProfileId: z.string().optional(),
  executionConfigOverrideJson: z
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

interface StrategyInstanceDialogProps {
  open: boolean;
  mode: "create" | "edit";
  instance?: StrategyInstance;
  onOpenChange: (open: boolean) => void;
  onSuccess: (action: "create" | "edit") => void;
}

export default function StrategyInstanceDialog({
  open,
  mode,
  instance,
  onOpenChange,
  onSuccess,
}: StrategyInstanceDialogProps) {
  const templates = useStrategyTemplates();
  const accounts = useBrokerAccounts();
  const models = useModelProfiles({ purpose: "trading" });
  const create = useCreateStrategyInstance();
  const update = useUpdateStrategyInstance();
  const isSaving = create.isPending || update.isPending;

  // §10.2 — lifecycle 별 편집 제약
  const lifecycle = instance?.lifecycleState;
  const isDraft = lifecycle === "draft";
  const allowNameModeAccountEdit =
    mode === "create" || lifecycle === "draft" || lifecycle === "inactive";
  const allowBudgetEdit = mode === "create" || isDraft;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      strategyTemplateId: instance?.strategyTemplateId ?? "",
      name: instance?.name ?? "",
      executionMode: instance?.executionMode ?? "paper",
      brokerAccountId: instance?.brokerAccountId ?? "",
      budgetAmount: instance?.budgetAmount ?? 0,
      tradingModelProfileId: instance?.tradingModelProfileId ?? "",
      executionConfigOverrideJson: stringifyJsonRecord(
        instance?.executionConfigOverride ?? undefined,
      ),
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        strategyTemplateId: instance?.strategyTemplateId ?? "",
        name: instance?.name ?? "",
        executionMode: instance?.executionMode ?? "paper",
        brokerAccountId: instance?.brokerAccountId ?? "",
        budgetAmount: instance?.budgetAmount ?? 0,
        tradingModelProfileId: instance?.tradingModelProfileId ?? "",
        executionConfigOverrideJson: stringifyJsonRecord(
          instance?.executionConfigOverride ?? undefined,
        ),
      });
    }
  }, [open, instance, reset]);

  const onSubmit = handleSubmit(async (values) => {
    const executionConfig = values.executionConfigOverrideJson
      ? parseJsonRecord(values.executionConfigOverrideJson)
      : null;
    const brokerAccountId =
      values.brokerAccountId && values.brokerAccountId.trim() !== ""
        ? values.brokerAccountId.trim()
        : null;
    const tradingModelProfileId =
      values.tradingModelProfileId &&
      values.tradingModelProfileId.trim() !== ""
        ? values.tradingModelProfileId.trim()
        : null;

    if (mode === "create") {
      await create.mutateAsync({
        strategyTemplateId: values.strategyTemplateId,
        name: values.name.trim(),
        executionMode: values.executionMode,
        brokerAccountId,
        budgetAmount: values.budgetAmount,
        tradingModelProfileId,
        executionConfigOverride: executionConfig,
      });
      onSuccess("create");
    } else if (instance) {
      await update.mutateAsync({
        id: instance.id,
        body: {
          name: values.name.trim(),
          executionMode: values.executionMode,
          budgetAmount: values.budgetAmount,
          brokerAccountId,
          tradingModelProfileId,
          executionConfigOverride: executionConfig,
          version: instance.version,
        },
      });
      onSuccess("edit");
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? "전략 인스턴스 추가" : "전략 인스턴스 수정"}
          </DialogTitle>
          <DialogDescription>
            {mode === "create"
              ? "신규 인스턴스는 항상 draft 상태로 생성됩니다."
              : "변경된 설정은 다음 사이클부터 반영됩니다. lifecycle 제약은 §10.2 를 따릅니다."}
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="instance-template"
            label="템플릿"
            required
            error={errors.strategyTemplateId?.message}
          >
            <select
              id="instance-template"
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
              disabled={mode === "edit"}
              {...register("strategyTemplateId")}
            >
              <option value="">— 선택 —</option>
              {(templates.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </FormField>

          <FormField
            id="instance-name"
            label="이름"
            required
            helpText={
              !allowNameModeAccountEdit
                ? "active 상태에서는 이름 변경이 불가합니다. (§10.2)"
                : undefined
            }
            error={errors.name?.message}
          >
            <Input
              id="instance-name"
              disabled={!allowNameModeAccountEdit}
              {...register("name")}
            />
          </FormField>

          <FormField
            id="instance-mode"
            label="실행 모드"
            required
            helpText={
              !allowNameModeAccountEdit
                ? "active 상태에서는 모드 변경이 불가합니다."
                : "live 모드는 연결 계좌 필수."
            }
            error={errors.executionMode?.message}
          >
            <select
              id="instance-mode"
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
              disabled={!allowNameModeAccountEdit}
              {...register("executionMode")}
            >
              {EXECUTION_MODES.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </FormField>

          <FormField
            id="instance-budget"
            label="전략 예산 (KRW)"
            required
            helpText={
              !allowBudgetEdit
                ? "예산은 draft 에서만 변경 가능합니다. (§10.2)"
                : undefined
            }
            error={errors.budgetAmount?.message}
          >
            <Input
              id="instance-budget"
              type="number"
              min={1}
              disabled={!allowBudgetEdit}
              {...register("budgetAmount", { valueAsNumber: true })}
            />
          </FormField>

          <FormField
            id="instance-broker"
            label="연결 계좌"
            helpText={
              !allowNameModeAccountEdit
                ? "active 상태에서는 변경 불가."
                : "live 모드 인스턴스 활성화 전에 필수."
            }
            error={errors.brokerAccountId?.message}
          >
            <select
              id="instance-broker"
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
              disabled={!allowNameModeAccountEdit}
              {...register("brokerAccountId")}
            >
              <option value="">— 없음 —</option>
              {(accounts.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.alias} ({a.accountNumberMasked})
                </option>
              ))}
            </select>
          </FormField>

          <FormField
            id="instance-model"
            label="트레이딩 모델 프로필"
            helpText="비워두면 템플릿 기본값을 사용합니다."
            error={errors.tradingModelProfileId?.message}
          >
            <select
              id="instance-model"
              className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              {...register("tradingModelProfileId")}
            >
              <option value="">— 템플릿 기본값 —</option>
              {(models.data ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name} ({m.modelName})
                </option>
              ))}
            </select>
          </FormField>

          <FormField
            id="instance-exec-config"
            label="실행 설정 오버라이드 (JSON)"
            error={errors.executionConfigOverrideJson?.message}
          >
            <Textarea
              id="instance-exec-config"
              className="min-h-[100px] font-mono text-xs"
              placeholder='{ "cycleMinutes": 5 }'
              {...register("executionConfigOverrideJson")}
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
