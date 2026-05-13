import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import { useCurrentUser } from "@/lib/auth";
import type {
  ApiEnvelope,
  AutoPausedReason,
  ExecutionMode,
  LifecycleState,
  StrategyInstance,
  StrategyInstanceCreateRequest,
  StrategyInstanceDuplicateRequest,
  StrategyInstanceLifecycleRequest,
  StrategyInstanceListFilter,
  StrategyOverviewCard,
  StrategyInstanceUpdateRequest,
} from "@/lib/api-types";

/**
 * 전략 인스턴스 CRUD + 상태 전환 + 복제 hook — docs/04-api-spec.md §8.4~8.8.
 */

export const STRATEGY_INSTANCES_KEY = ["admin", "strategy-instances"] as const;
export const SELECTABLE_STRATEGY_INSTANCES_KEY = [
  "strategy-instance-selector-options",
] as const;

export interface StrategyInstanceSelectorOption {
  id: string;
  name: string;
  executionMode: ExecutionMode;
  lifecycleState: LifecycleState;
  autoPausedReason: AutoPausedReason;
  watchlistCount: number | null;
  source: "admin" | "public";
}

function buildQuery(filter: StrategyInstanceListFilter): string {
  const params = new URLSearchParams();
  if (filter.lifecycleState) {
    params.set("lifecycleState", filter.lifecycleState);
  }
  if (filter.executionMode) {
    params.set("executionMode", filter.executionMode);
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

async function fetchInstances(
  filter: StrategyInstanceListFilter,
): Promise<StrategyInstance[]> {
  try {
    const res = await api.get<ApiEnvelope<StrategyInstance[]>>(
      `/admin/strategy-instances${buildQuery(filter)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchPublicInstances(): Promise<StrategyOverviewCard[]> {
  try {
    const res = await api.get<ApiEnvelope<StrategyOverviewCard[]>>(
      "/dashboard/strategy-overview",
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createInstance(
  body: StrategyInstanceCreateRequest,
): Promise<StrategyInstance> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<StrategyInstance>>(
      "/admin/strategy-instances",
      body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function updateInstance(args: {
  id: string;
  body: StrategyInstanceUpdateRequest;
}): Promise<StrategyInstance> {
  await ensureCsrfToken();
  try {
    const res = await api.patch<ApiEnvelope<StrategyInstance>>(
      `/admin/strategy-instances/${encodeURIComponent(args.id)}`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function transitionLifecycle(args: {
  id: string;
  body: StrategyInstanceLifecycleRequest;
}): Promise<StrategyInstance> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<StrategyInstance>>(
      `/admin/strategy-instances/${encodeURIComponent(args.id)}/lifecycle`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function duplicateInstance(args: {
  id: string;
  body: StrategyInstanceDuplicateRequest;
}): Promise<StrategyInstance> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<StrategyInstance>>(
      `/admin/strategy-instances/${encodeURIComponent(args.id)}/duplicate`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useStrategyInstances(filter: StrategyInstanceListFilter = {}) {
  return useQuery<StrategyInstance[], ApiError>({
    queryKey: [...STRATEGY_INSTANCES_KEY, filter],
    queryFn: () => fetchInstances(filter),
  });
}

export function useSelectableStrategyInstances() {
  const { data: currentUser, isLoading: isCurrentUserLoading } =
    useCurrentUser();

  return useQuery<StrategyInstanceSelectorOption[], ApiError>({
    queryKey: [
      ...SELECTABLE_STRATEGY_INSTANCES_KEY,
      currentUser ? "admin" : "public",
    ],
    queryFn: async () => {
      if (currentUser) {
        const instances = await fetchInstances({});
        return instances.map((instance) => ({
          id: instance.id,
          name: instance.name,
          executionMode: instance.executionMode,
          lifecycleState: instance.lifecycleState,
          autoPausedReason: instance.autoPausedReason,
          watchlistCount: null,
          source: "admin" as const,
        }));
      }

      const overview = await fetchPublicInstances();
      return overview.map((instance) => ({
        id: instance.strategyInstanceId,
        name: instance.name,
        executionMode: instance.executionMode,
        lifecycleState: instance.lifecycleState,
        autoPausedReason: instance.autoPausedReason,
        watchlistCount: instance.watchlistCount,
        source: "public" as const,
      }));
    },
    enabled: !isCurrentUserLoading,
    staleTime: 30 * 1000,
  });
}

export function useCreateStrategyInstance() {
  const queryClient = useQueryClient();
  return useMutation<StrategyInstance, ApiError, StrategyInstanceCreateRequest>({
    mutationFn: createInstance,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STRATEGY_INSTANCES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: STRATEGY_INSTANCES_KEY,
      });
    },
  });
}

export function useUpdateStrategyInstance() {
  const queryClient = useQueryClient();
  return useMutation<
    StrategyInstance,
    ApiError,
    { id: string; body: StrategyInstanceUpdateRequest }
  >({
    mutationFn: updateInstance,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STRATEGY_INSTANCES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: STRATEGY_INSTANCES_KEY,
      });
    },
  });
}

export function useTransitionStrategyInstance() {
  const queryClient = useQueryClient();
  return useMutation<
    StrategyInstance,
    ApiError,
    { id: string; body: StrategyInstanceLifecycleRequest }
  >({
    mutationFn: transitionLifecycle,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STRATEGY_INSTANCES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: STRATEGY_INSTANCES_KEY,
      });
    },
  });
}

export function useDuplicateStrategyInstance() {
  const queryClient = useQueryClient();
  return useMutation<
    StrategyInstance,
    ApiError,
    { id: string; body: StrategyInstanceDuplicateRequest }
  >({
    mutationFn: duplicateInstance,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STRATEGY_INSTANCES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: STRATEGY_INSTANCES_KEY,
      });
    },
  });
}
