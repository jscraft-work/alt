import { useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiEnvelope,
  PaperEvalDailyPoint,
  PaperEvalMetricSnapshot,
  PaperEvalRecentMatch,
} from "@/lib/api-types";

/**
 * 박스 단타 v1 paper-eval 화면 (F1) 의 데이터 hook.
 *
 * - 백엔드: {@code PaperEvalController}
 *   - `GET /api/admin/paper-eval/{instanceId}?lookback=N` → MetricSnapshot
 *   - `GET /api/admin/paper-eval/{instanceId}/series?days=D` → DailyPnlPoint[]
 *   - `GET /api/admin/paper-eval/{instanceId}/recent-matches?limit=N` → RecentMatchView[]
 *
 * - staleTime: 30s (대시보드 폴링과 정합 — QueryClient default 와 동일)
 */

export const PAPER_EVAL_BASE_KEY = ["admin", "paper-eval"] as const;

export const paperEvalSnapshotKey = (instanceId: string, lookback: number) =>
  [...PAPER_EVAL_BASE_KEY, instanceId, "snapshot", lookback] as const;

export const paperEvalSeriesKey = (instanceId: string, days: number) =>
  [...PAPER_EVAL_BASE_KEY, instanceId, "series", days] as const;

export const paperEvalRecentMatchesKey = (
  instanceId: string,
  limit: number,
) => [...PAPER_EVAL_BASE_KEY, instanceId, "recent-matches", limit] as const;

async function fetchSnapshot(
  instanceId: string,
  lookback: number,
): Promise<PaperEvalMetricSnapshot> {
  try {
    const res = await api.get<ApiEnvelope<PaperEvalMetricSnapshot>>(
      `/admin/paper-eval/${encodeURIComponent(instanceId)}`,
      { params: { lookback } },
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchSeries(
  instanceId: string,
  days: number,
): Promise<PaperEvalDailyPoint[]> {
  try {
    const res = await api.get<ApiEnvelope<PaperEvalDailyPoint[]>>(
      `/admin/paper-eval/${encodeURIComponent(instanceId)}/series`,
      { params: { days } },
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchRecentMatches(
  instanceId: string,
  limit: number,
): Promise<PaperEvalRecentMatch[]> {
  try {
    const res = await api.get<ApiEnvelope<PaperEvalRecentMatch[]>>(
      `/admin/paper-eval/${encodeURIComponent(instanceId)}/recent-matches`,
      { params: { limit } },
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function usePaperEvalSnapshot(
  instanceId: string | null,
  lookback: number,
) {
  return useQuery<PaperEvalMetricSnapshot, ApiError>({
    queryKey: instanceId
      ? paperEvalSnapshotKey(instanceId, lookback)
      : [...PAPER_EVAL_BASE_KEY, "snapshot", "none"],
    queryFn: () => fetchSnapshot(instanceId as string, lookback),
    enabled: !!instanceId,
  });
}

export function usePaperEvalSeries(
  instanceId: string | null,
  days: number,
) {
  return useQuery<PaperEvalDailyPoint[], ApiError>({
    queryKey: instanceId
      ? paperEvalSeriesKey(instanceId, days)
      : [...PAPER_EVAL_BASE_KEY, "series", "none"],
    queryFn: () => fetchSeries(instanceId as string, days),
    enabled: !!instanceId,
  });
}

export function usePaperEvalRecentMatches(
  instanceId: string | null,
  limit: number,
) {
  return useQuery<PaperEvalRecentMatch[], ApiError>({
    queryKey: instanceId
      ? paperEvalRecentMatchesKey(instanceId, limit)
      : [...PAPER_EVAL_BASE_KEY, "recent-matches", "none"],
    queryFn: () => fetchRecentMatches(instanceId as string, limit),
    enabled: !!instanceId,
  });
}
