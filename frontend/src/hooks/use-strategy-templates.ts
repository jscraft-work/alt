import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  StrategyTemplate,
  StrategyTemplateCreateRequest,
  StrategyTemplateUpdateRequest,
} from "@/lib/api-types";

/**
 * 전략 템플릿 CRUD hook — docs/04-api-spec.md §8.1~8.3.
 */

export const STRATEGY_TEMPLATES_KEY = ["admin", "strategy-templates"] as const;

async function fetchTemplates(): Promise<StrategyTemplate[]> {
  try {
    const res = await api.get<ApiEnvelope<StrategyTemplate[]>>(
      "/admin/strategy-templates",
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createTemplate(
  body: StrategyTemplateCreateRequest,
): Promise<StrategyTemplate> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<StrategyTemplate>>(
      "/admin/strategy-templates",
      body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function updateTemplate(args: {
  id: string;
  body: StrategyTemplateUpdateRequest;
}): Promise<StrategyTemplate> {
  await ensureCsrfToken();
  try {
    const res = await api.patch<ApiEnvelope<StrategyTemplate>>(
      `/admin/strategy-templates/${encodeURIComponent(args.id)}`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useStrategyTemplates() {
  return useQuery<StrategyTemplate[], ApiError>({
    queryKey: STRATEGY_TEMPLATES_KEY,
    queryFn: fetchTemplates,
  });
}

export function useCreateStrategyTemplate() {
  const queryClient = useQueryClient();
  return useMutation<StrategyTemplate, ApiError, StrategyTemplateCreateRequest>({
    mutationFn: createTemplate,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STRATEGY_TEMPLATES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: STRATEGY_TEMPLATES_KEY,
      });
    },
  });
}

export function useUpdateStrategyTemplate() {
  const queryClient = useQueryClient();
  return useMutation<
    StrategyTemplate,
    ApiError,
    { id: string; body: StrategyTemplateUpdateRequest }
  >({
    mutationFn: updateTemplate,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STRATEGY_TEMPLATES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: STRATEGY_TEMPLATES_KEY,
      });
    },
  });
}
