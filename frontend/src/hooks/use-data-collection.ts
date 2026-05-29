import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  DataCollectionSummary,
  OpsEventService,
  OpsEventView,
  WsSubscribeRequest,
  WsSubscribeResponse,
  WsSubscriptionList,
  WsUnsubscribeResponse,
} from "@/lib/api-types";

/**
 * data-collection admin (F2) — 4 섹션 통합 모니터링 + KIS WS manual subscribe CRUD.
 *
 * 백엔드: DataCollectionAdminController
 * - GET /api/admin/data-collection/summary
 * - GET /api/admin/data-collection/ws/subscriptions
 * - POST /api/admin/data-collection/ws/subscribe body: {symbolCodes: [...]}
 * - DELETE /api/admin/data-collection/ws/subscribe/{symbolCode}
 * - GET /api/admin/data-collection/ops-events?service=...&limit=N
 */

export const DATA_COLLECTION_KEY = ["admin", "data-collection"] as const;
export const DATA_COLLECTION_SUMMARY_KEY = [
  ...DATA_COLLECTION_KEY,
  "summary",
] as const;
export const DATA_COLLECTION_SUBSCRIPTIONS_KEY = [
  ...DATA_COLLECTION_KEY,
  "subscriptions",
] as const;
export const dataCollectionOpsEventsKey = (
  service: OpsEventService,
  limit: number,
) => [...DATA_COLLECTION_KEY, "ops-events", service, limit] as const;

async function fetchSummary(): Promise<DataCollectionSummary> {
  try {
    const res = await api.get<ApiEnvelope<DataCollectionSummary>>(
      "/admin/data-collection/summary",
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchSubscriptions(): Promise<WsSubscriptionList> {
  try {
    const res = await api.get<ApiEnvelope<WsSubscriptionList>>(
      "/admin/data-collection/ws/subscriptions",
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchOpsEvents(
  service: OpsEventService,
  limit: number,
): Promise<OpsEventView[]> {
  try {
    const res = await api.get<ApiEnvelope<OpsEventView[]>>(
      "/admin/data-collection/ops-events",
      { params: { service, limit } },
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function postSubscribe(
  body: WsSubscribeRequest,
): Promise<WsSubscribeResponse> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<WsSubscribeResponse>>(
      "/admin/data-collection/ws/subscribe",
      body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function deleteSubscription(
  symbolCode: string,
): Promise<WsUnsubscribeResponse> {
  await ensureCsrfToken();
  try {
    const res = await api.delete<ApiEnvelope<WsUnsubscribeResponse>>(
      `/admin/data-collection/ws/subscribe/${encodeURIComponent(symbolCode)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

/** 30초마다 자동 refetch — 운영자가 모니터링 페이지에 머무를 때 자동 갱신 */
export function useDataCollectionSummary() {
  return useQuery<DataCollectionSummary, ApiError>({
    queryKey: DATA_COLLECTION_SUMMARY_KEY,
    queryFn: fetchSummary,
    refetchInterval: 30_000,
  });
}

export function useWsSubscriptions() {
  return useQuery<WsSubscriptionList, ApiError>({
    queryKey: DATA_COLLECTION_SUBSCRIPTIONS_KEY,
    queryFn: fetchSubscriptions,
    refetchInterval: 30_000,
  });
}

export function useOpsEvents(service: OpsEventService, limit = 10) {
  return useQuery<OpsEventView[], ApiError>({
    queryKey: dataCollectionOpsEventsKey(service, limit),
    queryFn: () => fetchOpsEvents(service, limit),
    refetchInterval: 30_000,
  });
}

export function useWsSubscribe() {
  const queryClient = useQueryClient();
  return useMutation<WsSubscribeResponse, ApiError, WsSubscribeRequest>({
    mutationFn: postSubscribe,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: DATA_COLLECTION_SUBSCRIPTIONS_KEY,
      });
      void queryClient.invalidateQueries({
        queryKey: DATA_COLLECTION_SUMMARY_KEY,
      });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: DATA_COLLECTION_SUBSCRIPTIONS_KEY,
      });
    },
  });
}

export function useWsUnsubscribe() {
  const queryClient = useQueryClient();
  return useMutation<WsUnsubscribeResponse, ApiError, string>({
    mutationFn: deleteSubscription,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: DATA_COLLECTION_SUBSCRIPTIONS_KEY,
      });
      void queryClient.invalidateQueries({
        queryKey: DATA_COLLECTION_SUMMARY_KEY,
      });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: DATA_COLLECTION_SUBSCRIPTIONS_KEY,
      });
    },
  });
}
