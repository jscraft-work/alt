import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  AssetMaster,
  AssetMasterCreateRequest,
  AssetMasterListFilter,
  AssetMasterUpdateRequest,
  DartCorpCodeLookupResult,
} from "@/lib/api-types";

/**
 * 글로벌 종목 마스터 CRUD hook — docs/04-api-spec.md §8.19~8.20.
 */

export const ASSET_MASTERS_KEY = ["admin", "asset-masters"] as const;

function buildQuery(filter: AssetMasterListFilter): string {
  const params = new URLSearchParams();
  if (filter.q) {
    params.set("q", filter.q);
  }
  if (typeof filter.hidden === "boolean") {
    params.set("hidden", String(filter.hidden));
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

async function fetchAssetMasters(
  filter: AssetMasterListFilter,
): Promise<AssetMaster[]> {
  try {
    const res = await api.get<ApiEnvelope<AssetMaster[]>>(
      `/admin/assets${buildQuery(filter)}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createAssetMaster(
  body: AssetMasterCreateRequest,
): Promise<AssetMaster> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<AssetMaster>>("/admin/assets", body);
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function updateAssetMaster(args: {
  id: string;
  body: AssetMasterUpdateRequest;
}): Promise<AssetMaster> {
  await ensureCsrfToken();
  try {
    const res = await api.patch<ApiEnvelope<AssetMaster>>(
      `/admin/assets/${encodeURIComponent(args.id)}`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function lookupDartCorpCode(
  symbolCode: string,
): Promise<DartCorpCodeLookupResult> {
  try {
    const res = await api.get<ApiEnvelope<DartCorpCodeLookupResult>>(
      `/admin/assets/dart-corp-code-lookup?symbolCode=${encodeURIComponent(symbolCode.trim())}`,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useAssetMasters(
  filter: AssetMasterListFilter = {},
  options?: { enabled?: boolean },
) {
  return useQuery<AssetMaster[], ApiError>({
    queryKey: [...ASSET_MASTERS_KEY, filter],
    queryFn: () => fetchAssetMasters(filter),
    enabled: options?.enabled ?? true,
  });
}

export function useCreateAssetMaster() {
  const queryClient = useQueryClient();
  return useMutation<AssetMaster, ApiError, AssetMasterCreateRequest>({
    mutationFn: createAssetMaster,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ASSET_MASTERS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: ASSET_MASTERS_KEY,
      });
    },
  });
}

export function useUpdateAssetMaster() {
  const queryClient = useQueryClient();
  return useMutation<
    AssetMaster,
    ApiError,
    { id: string; body: AssetMasterUpdateRequest }
  >({
    mutationFn: updateAssetMaster,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ASSET_MASTERS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: ASSET_MASTERS_KEY,
      });
    },
  });
}

export function useAssetMasterDartCorpCodeLookup() {
  return useMutation<DartCorpCodeLookupResult, ApiError, string>({
    mutationFn: lookupDartCorpCode,
  });
}
