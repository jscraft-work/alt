import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  WatchlistAddRequest,
  WatchlistItem,
} from "@/lib/api-types";

/**
 * 인스턴스 감시 종목 hook — docs/04-api-spec.md §8.12~8.14.
 */

export const watchlistKey = (instanceId: string) =>
  ["admin", "strategy-instances", instanceId, "watchlist"] as const;

async function fetchWatchlist(instanceId: string): Promise<WatchlistItem[]> {
  try {
    const res = await api.get<ApiEnvelope<WatchlistItem[]>>(
      `/admin/strategy-instances/${encodeURIComponent(instanceId)}/watchlist`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function addWatchlist(args: {
  instanceId: string;
  body: WatchlistAddRequest;
}): Promise<WatchlistItem> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<WatchlistItem>>(
      `/admin/strategy-instances/${encodeURIComponent(args.instanceId)}/watchlist`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function removeWatchlist(args: {
  instanceId: string;
  assetMasterId: string;
}): Promise<void> {
  await ensureCsrfToken();
  try {
    await api.delete(
      `/admin/strategy-instances/${encodeURIComponent(args.instanceId)}/watchlist/${encodeURIComponent(args.assetMasterId)}`,
    );
  } catch (error) {
    throw toApiError(error);
  }
}

export function useWatchlist(instanceId: string | null) {
  return useQuery<WatchlistItem[], ApiError>({
    queryKey: instanceId ? watchlistKey(instanceId) : ["admin", "watchlist", "none"],
    queryFn: () => fetchWatchlist(instanceId as string),
    enabled: !!instanceId,
  });
}

export function useAddWatchlist(instanceId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    WatchlistItem,
    ApiError,
    { instanceId: string; body: WatchlistAddRequest }
  >({
    mutationFn: addWatchlist,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: watchlistKey(instanceId),
      });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: watchlistKey(instanceId),
      });
    },
  });
}

export function useRemoveWatchlist(instanceId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    void,
    ApiError,
    { instanceId: string; assetMasterId: string }
  >({
    mutationFn: removeWatchlist,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: watchlistKey(instanceId),
      });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: watchlistKey(instanceId),
      });
    },
  });
}
