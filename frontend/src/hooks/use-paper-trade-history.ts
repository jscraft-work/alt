import { useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiEnvelope,
  TradeHistoryFilter,
  TradeHistoryResult,
} from "@/lib/api-types";

/**
 * F3 — paper_trade_match 페이지네이션/필터 조회 hook.
 *
 * 백엔드: PaperEvalController.GET /api/admin/paper-eval/{instanceId}/trade-history
 */

export const TRADE_HISTORY_BASE_KEY = ["admin", "paper-eval", "trade-history"] as const;

export const tradeHistoryKey = (instanceId: string, filter: TradeHistoryFilter) =>
  [
    ...TRADE_HISTORY_BASE_KEY,
    instanceId,
    filter.page ?? 0,
    filter.size ?? 50,
    filter.from ?? null,
    filter.to ?? null,
    (filter.symbol ?? "").trim().toLowerCase() || null,
    filter.winOnly ?? false,
    filter.lossOnly ?? false,
    filter.sort ?? "exit_time:desc",
  ] as const;

async function fetchTradeHistory(
  instanceId: string,
  filter: TradeHistoryFilter,
): Promise<TradeHistoryResult> {
  const params: Record<string, string | number | boolean> = {
    page: filter.page ?? 0,
    size: filter.size ?? 50,
    winOnly: filter.winOnly ?? false,
    lossOnly: filter.lossOnly ?? false,
    sort: filter.sort ?? "exit_time:desc",
  };
  if (filter.from) {
    params.from = filter.from;
  }
  if (filter.to) {
    params.to = filter.to;
  }
  if (filter.symbol && filter.symbol.trim()) {
    params.symbol = filter.symbol.trim();
  }
  try {
    const res = await api.get<ApiEnvelope<TradeHistoryResult>>(
      `/admin/paper-eval/${encodeURIComponent(instanceId)}/trade-history`,
      { params },
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function usePaperTradeHistory(
  instanceId: string | null,
  filter: TradeHistoryFilter,
) {
  return useQuery<TradeHistoryResult, ApiError>({
    queryKey: instanceId
      ? tradeHistoryKey(instanceId, filter)
      : [...TRADE_HISTORY_BASE_KEY, "none"],
    queryFn: () => fetchTradeHistory(instanceId as string, filter),
    enabled: !!instanceId,
    // 페이지 전환 시 깜빡임 방지: placeholderData
    placeholderData: (prev) => prev,
  });
}
