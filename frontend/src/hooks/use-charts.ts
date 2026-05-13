import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiEnvelope,
  ChartMinuteBarsFilter,
  ChartOrderOverlay,
  ChartOrderOverlayFilter,
  MinuteBarsResponse,
} from "@/lib/api-types";

export const CHART_MINUTE_BARS_KEY = ["charts", "minutes"] as const;
export const CHART_ORDER_OVERLAYS_KEY = ["charts", "order-overlays"] as const;

function buildChartQuery(
  params: Record<string, string | null | undefined>,
): string {
  const searchParams = new URLSearchParams();

  for (const [key, value] of Object.entries(params)) {
    if (!value) {
      continue;
    }
    searchParams.set(key, value);
  }

  const queryString = searchParams.toString();
  return queryString ? `?${queryString}` : "";
}

async function fetchMinuteBars(
  filter: ChartMinuteBarsFilter,
): Promise<MinuteBarsResponse> {
  try {
    const res = await api.get<ApiEnvelope<MinuteBarsResponse>>(
      `/charts/minutes${buildChartQuery({
        symbolCode: filter.symbolCode,
        date: filter.date,
      })}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchOrderOverlays(
  filter: ChartOrderOverlayFilter,
): Promise<ChartOrderOverlay[]> {
  try {
    const res = await api.get<ApiEnvelope<ChartOrderOverlay[]>>(
      `/charts/order-overlays${buildChartQuery({
        symbolCode: filter.symbolCode,
        date: filter.date,
        strategyInstanceId: filter.strategyInstanceId ?? null,
      })}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useMinuteBars(
  filter: ChartMinuteBarsFilter,
  enabled = true,
) {
  return useQuery<MinuteBarsResponse, ApiError>({
    queryKey: [...CHART_MINUTE_BARS_KEY, filter],
    queryFn: () => fetchMinuteBars(filter),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useOrderOverlays(
  filter: ChartOrderOverlayFilter,
  enabled = true,
) {
  return useQuery<ChartOrderOverlay[], ApiError>({
    queryKey: [...CHART_ORDER_OVERLAYS_KEY, filter],
    queryFn: () => fetchOrderOverlays(filter),
    enabled,
    placeholderData: keepPreviousData,
  });
}
