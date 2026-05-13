import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  PromptVersion,
  PromptVersionActivateRequest,
  PromptVersionCreateRequest,
} from "@/lib/api-types";
import { STRATEGY_INSTANCES_KEY } from "@/hooks/use-strategy-instances";

/**
 * 인스턴스 프롬프트 버전 hook — docs/04-api-spec.md §8.9~8.11.
 */

export const promptVersionsKey = (instanceId: string) =>
  ["admin", "strategy-instances", instanceId, "prompt-versions"] as const;

async function fetchPromptVersions(instanceId: string): Promise<PromptVersion[]> {
  try {
    const res = await api.get<ApiEnvelope<PromptVersion[]>>(
      `/admin/strategy-instances/${encodeURIComponent(instanceId)}/prompt-versions`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createPromptVersion(args: {
  instanceId: string;
  body: PromptVersionCreateRequest;
}): Promise<PromptVersion> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<PromptVersion>>(
      `/admin/strategy-instances/${encodeURIComponent(args.instanceId)}/prompt-versions`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function activatePromptVersion(args: {
  instanceId: string;
  promptVersionId: string;
  body: PromptVersionActivateRequest;
}): Promise<PromptVersion> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<PromptVersion>>(
      `/admin/strategy-instances/${encodeURIComponent(args.instanceId)}/prompt-versions/${encodeURIComponent(args.promptVersionId)}/activate`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function usePromptVersions(instanceId: string | null) {
  return useQuery<PromptVersion[], ApiError>({
    queryKey: instanceId
      ? promptVersionsKey(instanceId)
      : ["admin", "prompt-versions", "none"],
    queryFn: () => fetchPromptVersions(instanceId as string),
    enabled: !!instanceId,
  });
}

export function useCreatePromptVersion(instanceId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    PromptVersion,
    ApiError,
    { instanceId: string; body: PromptVersionCreateRequest }
  >({
    mutationFn: createPromptVersion,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: promptVersionsKey(instanceId),
      });
      void queryClient.invalidateQueries({
        queryKey: STRATEGY_INSTANCES_KEY,
      });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: promptVersionsKey(instanceId),
      });
    },
  });
}

export function useActivatePromptVersion(instanceId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    PromptVersion,
    ApiError,
    {
      instanceId: string;
      promptVersionId: string;
      body: PromptVersionActivateRequest;
    }
  >({
    mutationFn: activatePromptVersion,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: promptVersionsKey(instanceId),
      });
      void queryClient.invalidateQueries({
        queryKey: STRATEGY_INSTANCES_KEY,
      });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: promptVersionsKey(instanceId),
      });
    },
  });
}
