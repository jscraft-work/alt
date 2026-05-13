import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  ModelProfile,
  ModelProfileCreateRequest,
  ModelProfileListFilter,
  ModelProfileUpdateRequest,
} from "@/lib/api-types";

/**
 * 모델 프로필 CRUD hook — docs/04-api-spec.md §8.15~8.16.
 */

export const MODEL_PROFILES_KEY = ["admin", "model-profiles"] as const;

function buildQuery(filter: ModelProfileListFilter): string {
  const params = new URLSearchParams();
  if (filter.purpose) {
    params.set("purpose", filter.purpose);
  }
  if (typeof filter.enabled === "boolean") {
    params.set("enabled", String(filter.enabled));
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

async function fetchProfiles(
  filter: ModelProfileListFilter,
): Promise<ModelProfile[]> {
  try {
    const res = await api.get<ApiEnvelope<ModelProfile[]>>(
      `/admin/model-profiles${buildQuery(filter)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createProfile(
  body: ModelProfileCreateRequest,
): Promise<ModelProfile> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<ModelProfile>>(
      "/admin/model-profiles",
      body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function updateProfile(args: {
  id: string;
  body: ModelProfileUpdateRequest;
}): Promise<ModelProfile> {
  await ensureCsrfToken();
  try {
    const res = await api.patch<ApiEnvelope<ModelProfile>>(
      `/admin/model-profiles/${encodeURIComponent(args.id)}`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useModelProfiles(filter: ModelProfileListFilter = {}) {
  return useQuery<ModelProfile[], ApiError>({
    queryKey: [...MODEL_PROFILES_KEY, filter],
    queryFn: () => fetchProfiles(filter),
  });
}

export function useCreateModelProfile() {
  const queryClient = useQueryClient();
  return useMutation<ModelProfile, ApiError, ModelProfileCreateRequest>({
    mutationFn: createProfile,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MODEL_PROFILES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: MODEL_PROFILES_KEY,
      });
    },
  });
}

export function useUpdateModelProfile() {
  const queryClient = useQueryClient();
  return useMutation<
    ModelProfile,
    ApiError,
    { id: string; body: ModelProfileUpdateRequest }
  >({
    mutationFn: updateProfile,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MODEL_PROFILES_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: MODEL_PROFILES_KEY,
      });
    },
  });
}
