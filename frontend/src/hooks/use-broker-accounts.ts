import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "@/lib/api";
import { handleMutationError } from "@/lib/api-error";
import type {
  ApiEnvelope,
  BrokerAccount,
  BrokerAccountCreateRequest,
  BrokerAccountUpdateRequest,
} from "@/lib/api-types";

/**
 * 브로커 계좌 CRUD hook — docs/04-api-spec.md §8.17~8.18.
 */

export const BROKER_ACCOUNTS_KEY = ["admin", "broker-accounts"] as const;

async function fetchAccounts(): Promise<BrokerAccount[]> {
  try {
    const res = await api.get<ApiEnvelope<BrokerAccount[]>>(
      "/admin/broker-accounts",
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function createAccount(
  body: BrokerAccountCreateRequest,
): Promise<BrokerAccount> {
  await ensureCsrfToken();
  try {
    const res = await api.post<ApiEnvelope<BrokerAccount>>(
      "/admin/broker-accounts",
      body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function updateAccount(args: {
  id: string;
  body: BrokerAccountUpdateRequest;
}): Promise<BrokerAccount> {
  await ensureCsrfToken();
  try {
    const res = await api.patch<ApiEnvelope<BrokerAccount>>(
      `/admin/broker-accounts/${encodeURIComponent(args.id)}`,
      args.body,
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useBrokerAccounts() {
  return useQuery<BrokerAccount[], ApiError>({
    queryKey: BROKER_ACCOUNTS_KEY,
    queryFn: fetchAccounts,
  });
}

export function useCreateBrokerAccount() {
  const queryClient = useQueryClient();
  return useMutation<BrokerAccount, ApiError, BrokerAccountCreateRequest>({
    mutationFn: createAccount,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BROKER_ACCOUNTS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: BROKER_ACCOUNTS_KEY,
      });
    },
  });
}

export function useUpdateBrokerAccount() {
  const queryClient = useQueryClient();
  return useMutation<
    BrokerAccount,
    ApiError,
    { id: string; body: BrokerAccountUpdateRequest }
  >({
    mutationFn: updateAccount,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BROKER_ACCOUNTS_KEY });
    },
    onError: (error) => {
      handleMutationError(error, {
        queryClient,
        queryKey: BROKER_ACCOUNTS_KEY,
      });
    },
  });
}
