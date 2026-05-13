import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryOptions,
} from "@tanstack/react-query";
import { ApiError, api, ensureCsrfToken, toApiError } from "./api";
import type {
  ApiEnvelope,
  AuthUser,
  LoginRequest,
  LoginResponse,
} from "./api-types";

/**
 * 인증 관련 hook 모음.
 *
 * - useCurrentUser: `GET /api/auth/me` (docs/04-api-spec.md §3.3)
 * - useLogin:       `POST /api/auth/login` (§3.1)
 * - useLogout:      `POST /api/auth/logout` (§3.2)
 *
 * me 호출은 401일 때 throw 하지 않고 `null`을 반환해, `RequireAuth` 가드가
 * `data === null`을 unauthenticated 신호로 사용하도록 한다.
 */

export const AUTH_QUERY_KEY = ["auth", "me"] as const;

async function fetchCurrentUser(): Promise<AuthUser | null> {
  try {
    const res = await api.get<ApiEnvelope<AuthUser>>("/auth/me");
    return res.data.data;
  } catch (error) {
    const apiError = toApiError(error);
    if (apiError.status === 401) {
      return null;
    }
    throw apiError;
  }
}

export function useCurrentUser(
  options?: Omit<
    UseQueryOptions<AuthUser | null, ApiError>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery<AuthUser | null, ApiError>({
    queryKey: AUTH_QUERY_KEY,
    queryFn: fetchCurrentUser,
    staleTime: 60 * 1000,
    retry: false,
    ...options,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation<AuthUser, ApiError, LoginRequest>({
    mutationFn: async (request) => {
      await ensureCsrfToken();
      try {
        const res = await api.post<ApiEnvelope<LoginResponse>>(
          "/auth/login",
          request,
        );
        return res.data.data.user;
      } catch (error) {
        throw toApiError(error);
      }
    },
    onSuccess: (user) => {
      queryClient.setQueryData(AUTH_QUERY_KEY, user);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation<void, ApiError, void>({
    mutationFn: async () => {
      await ensureCsrfToken();
      try {
        await api.post("/auth/logout");
      } catch (error) {
        throw toApiError(error);
      }
    },
    onSettled: () => {
      queryClient.setQueryData(AUTH_QUERY_KEY, null);
      // 인증 의존 데이터 전부 무효화
      void queryClient.invalidateQueries();
    },
  });
}
