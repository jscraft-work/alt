import { useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiEnvelope,
  InstanceDashboard,
  StrategyOverviewCard,
  SystemStatusItem,
} from "@/lib/api-types";

/**
 * 대시보드 데이터 hook.
 *
 * docs/04-api-spec.md §4.1 / §4.2 / §4.3 — 모두 비로그인 허용 조회 API.
 * spec §7.1 — 대시보드는 30초 주기 자동 갱신.
 */

export const STRATEGY_OVERVIEW_KEY = [
  "dashboard",
  "strategy-overview",
] as const;
export const SYSTEM_STATUS_KEY = ["dashboard", "system-status"] as const;
export const INSTANCE_DASHBOARD_KEY = ["dashboard", "instance"] as const;

const DASHBOARD_REFETCH_MS = 30 * 1000;

async function fetchStrategyOverview(): Promise<StrategyOverviewCard[]> {
  try {
    const res =
      await api.get<ApiEnvelope<StrategyOverviewCard[]>>(
        "/dashboard/strategy-overview",
      );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchSystemStatus(): Promise<SystemStatusItem[]> {
  try {
    const res =
      await api.get<ApiEnvelope<SystemStatusItem[]>>("/dashboard/system-status");
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchInstanceDashboard(
  strategyInstanceId: string,
): Promise<InstanceDashboard> {
  try {
    const res = await api.get<ApiEnvelope<InstanceDashboard>>(
      `/dashboard/instances/${encodeURIComponent(strategyInstanceId)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useStrategyOverview() {
  return useQuery<StrategyOverviewCard[], ApiError>({
    queryKey: STRATEGY_OVERVIEW_KEY,
    queryFn: fetchStrategyOverview,
    refetchInterval: DASHBOARD_REFETCH_MS,
    refetchIntervalInBackground: false,
  });
}

export function useSystemStatus() {
  return useQuery<SystemStatusItem[], ApiError>({
    queryKey: SYSTEM_STATUS_KEY,
    queryFn: fetchSystemStatus,
    refetchInterval: DASHBOARD_REFETCH_MS,
    refetchIntervalInBackground: false,
  });
}

export function useInstanceDashboard(strategyInstanceId: string | null) {
  return useQuery<InstanceDashboard, ApiError>({
    queryKey: [...INSTANCE_DASHBOARD_KEY, strategyInstanceId],
    queryFn: () => fetchInstanceDashboard(strategyInstanceId as string),
    enabled: !!strategyInstanceId,
    refetchInterval: DASHBOARD_REFETCH_MS,
    refetchIntervalInBackground: false,
  });
}
