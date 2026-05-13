import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiEnvelope,
  ApiPaged,
  TradeDecisionDetail,
  TradeDecisionListFilter,
  TradeDecisionListItem,
  TradeOrderDetail,
  TradeOrderListFilter,
  TradeOrderListItem,
} from "@/lib/api-types";

export const TRADE_ORDERS_KEY = ["trade-orders"] as const;
export const TRADE_DECISIONS_KEY = ["trade-decisions"] as const;

function buildTradeQuery(
  params: Record<string, string | number | null | undefined>,
) {
  const searchParams = new URLSearchParams();

  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    searchParams.set(key, String(value));
  }

  const qs = searchParams.toString();
  return qs ? `?${qs}` : "";
}

async function fetchTradeOrders(
  filter: TradeOrderListFilter,
): Promise<ApiPaged<TradeOrderListItem>> {
  try {
    const res = await api.get<ApiPaged<TradeOrderListItem>>(
      `/trade-orders${buildTradeQuery({
        strategyInstanceId: filter.strategyInstanceId,
        symbolCode: filter.symbolCode,
        orderStatus: filter.orderStatus,
        dateFrom: filter.dateFrom,
        dateTo: filter.dateTo,
        page: filter.page,
        size: filter.size,
      })}`,
    );
    return res.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchTradeOrderDetail(id: string): Promise<TradeOrderDetail> {
  try {
    const res = await api.get<ApiEnvelope<TradeOrderDetail>>(
      `/trade-orders/${encodeURIComponent(id)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchTradeDecisions(
  filter: TradeDecisionListFilter,
): Promise<ApiPaged<TradeDecisionListItem>> {
  try {
    const res = await api.get<ApiPaged<TradeDecisionListItem>>(
      `/trade-decisions${buildTradeQuery({
        strategyInstanceId: filter.strategyInstanceId,
        cycleStatus: filter.cycleStatus,
        dateFrom: filter.dateFrom,
        dateTo: filter.dateTo,
        page: filter.page,
        size: filter.size,
      })}`,
    );
    return res.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchTradeDecisionDetail(
  id: string,
): Promise<TradeDecisionDetail> {
  try {
    const res = await api.get<ApiEnvelope<TradeDecisionDetail>>(
      `/trade-decisions/${encodeURIComponent(id)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useTradeOrders(filter: TradeOrderListFilter) {
  return useQuery<ApiPaged<TradeOrderListItem>, ApiError>({
    queryKey: [...TRADE_ORDERS_KEY, filter],
    queryFn: () => fetchTradeOrders(filter),
    placeholderData: keepPreviousData,
  });
}

export function useTradeOrderDetail(id: string | null) {
  return useQuery<TradeOrderDetail, ApiError>({
    queryKey: [...TRADE_ORDERS_KEY, "detail", id],
    queryFn: () => fetchTradeOrderDetail(id as string),
    enabled: !!id,
  });
}

export function useTradeDecisions(filter: TradeDecisionListFilter) {
  return useQuery<ApiPaged<TradeDecisionListItem>, ApiError>({
    queryKey: [...TRADE_DECISIONS_KEY, filter],
    queryFn: () => fetchTradeDecisions(filter),
    placeholderData: keepPreviousData,
  });
}

export function useTradeDecisionDetail(id: string | null) {
  return useQuery<TradeDecisionDetail, ApiError>({
    queryKey: [...TRADE_DECISIONS_KEY, "detail", id],
    queryFn: () => fetchTradeDecisionDetail(id as string),
    enabled: !!id,
  });
}
