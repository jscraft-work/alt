import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  SystemParameter,
  SystemParameterCreateRequest,
  SystemParameterUpdateRequest,
} from "@/lib/api-types";

/**
 * 시스템 파라미터 CRUD hook — docs/04-api-spec.md §8.21.
 */

export const SYSTEM_PARAMETERS_KEY = ["admin", "system-parameters"] as const;

async function fetchSystemParameters(): Promise<SystemParameter[]> {
  try {
    const res = await api.get<ApiEnvelope<SystemParameter[]>>(
      "/admin/system-parameters",
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createSystemParameter(
  body: SystemParameterCreateRequest,
): Promise<SystemParameter> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<SystemParameter>>(
      "/admin/system-parameters",
      body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function updateSystemParameter(args: {
  parameterKey: string;
  body: SystemParameterUpdateRequest;
}): Promise<SystemParameter> {
  await ensureCsrfToken();
  try {
    const res = await api.patch<ApiEnvelope<SystemParameter>>(
      `/admin/system-parameters/${encodeURIComponent(args.parameterKey)}`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function deleteSystemParameter(parameterKey: string): Promise<void> {
  await ensureCsrfToken();
  try {
    await api.delete(`/admin/system-parameters/${encodeURIComponent(parameterKey)}`);
  } catch (error) {
    throw toApiError(error);
  }
}

export function useSystemParameters() {
  return useQuery<SystemParameter[], ApiError>({
    queryKey: SYSTEM_PARAMETERS_KEY,
    queryFn: fetchSystemParameters,
  });
}

export function useCreateSystemParameter() {
  const queryClient = useQueryClient();
  return useMutation<SystemParameter, ApiError, SystemParameterCreateRequest>({
    mutationFn: createSystemParameter,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: SYSTEM_PARAMETERS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: SYSTEM_PARAMETERS_KEY,
      });
    },
  });
}

export function useUpdateSystemParameter() {
  const queryClient = useQueryClient();
  return useMutation<
    SystemParameter,
    ApiError,
    { parameterKey: string; body: SystemParameterUpdateRequest }
  >({
    mutationFn: updateSystemParameter,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: SYSTEM_PARAMETERS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: SYSTEM_PARAMETERS_KEY,
      });
    },
  });
}

export function useDeleteSystemParameter() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, string>({
    mutationFn: deleteSystemParameter,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: SYSTEM_PARAMETERS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: SYSTEM_PARAMETERS_KEY,
      });
    },
  });
}
