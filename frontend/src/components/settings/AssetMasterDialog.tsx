import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Search } from "lucide-react";
import { toast } from "sonner";
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
  useAssetMasterDartCorpCodeLookup,
  useCreateAssetMaster,
  useUpdateAssetMaster,
} from "@/hooks/use-asset-masters";
import type { AssetMaster, DartCorpCodeLookupResult } from "@/lib/api-types";

/**
 * 글로벌 종목 마스터 생성/수정 — docs/04-api-spec.md §8.20.
 */

const schema = z.object({
  symbolCode: z
    .string()
    .min(1, "종목코드를 입력해 주세요.")
    .max(40, "종목코드는 40자 이하여야 합니다."),
  symbolName: z
    .string()
    .min(1, "종목명을 입력해 주세요.")
    .max(200, "종목명은 200자 이하여야 합니다."),
  marketType: z
    .string()
    .min(1, "시장 구분을 입력해 주세요.")
    .max(40, "시장 구분은 40자 이하여야 합니다."),
  dartCorpCode: z
    .string()
    .max(20, "DART 기업코드는 20자 이하여야 합니다.")
    .optional(),
  hidden: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

interface AssetMasterDialogProps {
  open: boolean;
  mode: "create" | "edit";
  asset?: AssetMaster;
  onOpenChange: (open: boolean) => void;
  onSuccess: (action: "create" | "edit") => void;
}

export default function AssetMasterDialog({
  open,
  mode,
  asset,
  onOpenChange,
  onSuccess,
}: AssetMasterDialogProps) {
  const create = useCreateAssetMaster();
  const update = useUpdateAssetMaster();
  const lookup = useAssetMasterDartCorpCodeLookup();
  const isSaving = create.isPending || update.isPending;
  const [lookupResult, setLookupResult] = useState<DartCorpCodeLookupResult | null>(
    null,
  );

  const {
    register,
    handleSubmit,
    reset,
    getValues,
    setValue,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      symbolCode: asset?.symbolCode ?? "",
      symbolName: asset?.symbolName ?? "",
      marketType: asset?.marketType ?? "",
      dartCorpCode: asset?.dartCorpCode ?? "",
      hidden: asset?.hidden ?? false,
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        symbolCode: asset?.symbolCode ?? "",
        symbolName: asset?.symbolName ?? "",
        marketType: asset?.marketType ?? "",
        dartCorpCode: asset?.dartCorpCode ?? "",
        hidden: asset?.hidden ?? false,
      });
    }
  }, [open, asset, reset]);

  const onLookup = async () => {
    const symbolCode = getValues("symbolCode").trim();
    if (!symbolCode) {
      setError("symbolCode", {
        type: "manual",
        message: "종목코드를 먼저 입력해 주세요.",
      });
      return;
    }

    clearErrors("symbolCode");

    try {
      const result = await lookup.mutateAsync(symbolCode);
      setLookupResult(result);
      setValue("dartCorpCode", result.dartCorpCode ?? "", {
        shouldDirty: true,
        shouldValidate: true,
      });
      if (!getValues("symbolName").trim()) {
        setValue("symbolName", result.symbolName, {
          shouldDirty: true,
          shouldValidate: true,
        });
      }
      toast.success("조회한 DART 기업코드를 반영했습니다.");
    } catch (error) {
      setLookupResult(null);
      toast.error(
        error instanceof Error ? error.message : "DART 기업코드 조회에 실패했습니다.",
      );
    }
  };

  const onSubmit = handleSubmit(async (values) => {
    const payload = {
      symbolCode: values.symbolCode.trim(),
      symbolName: values.symbolName.trim(),
      marketType: values.marketType.trim(),
      dartCorpCode: values.dartCorpCode?.trim() || undefined,
      hidden: values.hidden,
    };

    if (mode === "create") {
      await create.mutateAsync(payload);
      onSuccess("create");
      return;
    }

    if (asset) {
      await update.mutateAsync({
        id: asset.id,
        body: {
          ...payload,
          version: asset.version,
        },
      });
      onSuccess("edit");
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? "자산 마스터 추가" : "자산 마스터 수정"}
          </DialogTitle>
          <DialogDescription>
            글로벌 종목 기준 데이터와 DART 기업코드 매핑을 관리합니다.
          </DialogDescription>
        </DialogHeader>

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            void onSubmit(event);
          }}
        >
          <FormField
            id="asset-symbol-code"
            label="종목코드"
            required
            helpText="예: 005930"
            error={errors.symbolCode?.message}
          >
            <div className="flex gap-2">
              <Input
                id="asset-symbol-code"
                className="flex-1"
                {...register("symbolCode")}
              />
              <Button
                type="button"
                variant="outline"
                disabled={lookup.isPending}
                onClick={() => {
                  void onLookup();
                }}
              >
                {lookup.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Search className="size-4" />
                )}
                DART 조회
              </Button>
            </div>
          </FormField>

          {lookupResult && (
            <div className="rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
              조회 결과: {lookupResult.symbolName} / 자산 ID {lookupResult.assetMasterId}
              {" · "}
              {lookupResult.hidden ? "숨김" : "노출"}
            </div>
          )}

          <FormField
            id="asset-symbol-name"
            label="종목명"
            required
            error={errors.symbolName?.message}
          >
            <Input id="asset-symbol-name" {...register("symbolName")} />
          </FormField>

          <FormField
            id="asset-market-type"
            label="시장 구분"
            required
            helpText="예: KOSPI, KOSDAQ, ETF"
            error={errors.marketType?.message}
          >
            <Input id="asset-market-type" {...register("marketType")} />
          </FormField>

          <FormField
            id="asset-dart-corp-code"
            label="DART 기업코드"
            helpText="비워두면 수동 매핑 없이 저장합니다."
            error={errors.dartCorpCode?.message}
          >
            <Input id="asset-dart-corp-code" {...register("dartCorpCode")} />
          </FormField>

          <div className="flex items-center gap-2">
            <input
              id="asset-hidden"
              type="checkbox"
              className="size-4"
              {...register("hidden")}
            />
            <label htmlFor="asset-hidden" className="text-sm">
              숨김 처리
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
