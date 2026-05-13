import { toast } from "sonner";
import type { QueryClient, QueryKey } from "@tanstack/react-query";
import { ApiError } from "./api";

/**
 * 설정 CRUD 전용 mutation 에러 핸들러.
 *
 * docs/04-api-spec.md §2.4 — 수정 API 는 `version` 을 포함하며 서버는 충돌 시
 * `OPTIMISTIC_LOCK_CONFLICT` (HTTP 409) 를 반환한다.
 *
 * 처리:
 * - 409 / OPTIMISTIC_LOCK_CONFLICT → "다른 사용자가 먼저 수정했습니다." toast +
 *   해당 list query 를 invalidate 해 최신값을 다시 불러온다.
 * - VALIDATION_ERROR → 첫 fieldError 메시지 우선으로 toast.
 * - 그 외 → server 가 보낸 메시지를 그대로 toast.
 */
export function handleMutationError(
  error: ApiError,
  options: { queryClient: QueryClient; queryKey: QueryKey },
): void {
  if (
    error.code === "OPTIMISTIC_LOCK_CONFLICT" ||
    error.status === 409
  ) {
    toast.error("다른 사용자가 먼저 수정했습니다.", {
      description: "최신 상태로 다시 불러왔습니다. 다시 시도해 주세요.",
    });
    void options.queryClient.invalidateQueries({ queryKey: options.queryKey });
    return;
  }
  if (error.code === "VALIDATION_ERROR" && error.fieldErrors.length > 0) {
    const first = error.fieldErrors[0];
    toast.error("입력값을 확인해 주세요.", {
      description: `${first.field}: ${first.message}`,
    });
    return;
  }
  toast.error(error.message);
}
