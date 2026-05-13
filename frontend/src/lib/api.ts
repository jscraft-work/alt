import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from "axios";
import type { ApiErrorBody, ApiErrorCode, ApiFieldError } from "./api-types";

/**
 * alt-java HTTP 클라이언트.
 *
 * - 쿠키 세션 인증 (docs/06-auth-security.md §4)
 * - CSRF double-submit cookie (docs/06-auth-security.md §5)
 *   - 쿠키 이름: `XSRF-TOKEN` (HttpOnly 아님)
 *   - 요청 헤더: `X-CSRF-TOKEN`
 *   - GET/HEAD/OPTIONS 는 CSRF 검사 대상이 아니므로 헤더를 붙이지 않는다.
 */

export const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "/api";

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-CSRF-TOKEN";

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

function readCookie(name: string): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const target = `${encodeURIComponent(name)}=`;
  const parts = document.cookie ? document.cookie.split("; ") : [];
  for (const part of parts) {
    if (part.startsWith(target)) {
      return decodeURIComponent(part.slice(target.length));
    }
  }
  return null;
}

let csrfBootstrapPromise: Promise<void> | null = null;

function attachCsrfHeader(config: InternalAxiosRequestConfig) {
  const method = (config.method ?? "get").toUpperCase();
  if (!UNSAFE_METHODS.has(method)) {
    return;
  }
  const token = readCookie(CSRF_COOKIE_NAME);
  if (token) {
    config.headers.set(CSRF_HEADER_NAME, token);
  }
}

function createApiClient(): AxiosInstance {
  const client = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
  });

  client.interceptors.request.use((config) => {
    attachCsrfHeader(config);
    return config;
  });

  return client;
}

export const api = createApiClient();

/**
 * CSRF 토큰 쿠키를 미리 발급/재발급한다.
 *
 * docs/04-api-spec.md §3.4 — `GET /api/auth/csrf` 호출 시 서버가 `XSRF-TOKEN`
 * 쿠키를 내려준다. 앱 시작 시 한 번, 그리고 상태 변경 요청 직전 토큰이 없으면
 * 호출한다. (요청 도중 중복 호출을 막기 위해 promise 캐시 사용.)
 */
export async function ensureCsrfToken(force = false): Promise<void> {
  if (!force && readCookie(CSRF_COOKIE_NAME)) {
    return;
  }
  if (!csrfBootstrapPromise) {
    csrfBootstrapPromise = api
      .get("/auth/csrf")
      .then(() => undefined)
      .finally(() => {
        csrfBootstrapPromise = null;
      });
  }
  await csrfBootstrapPromise;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  readonly fieldErrors: ApiFieldError[];
  readonly meta?: Record<string, unknown>;

  constructor(params: {
    status: number;
    code: ApiErrorCode;
    message: string;
    fieldErrors?: ApiFieldError[];
    meta?: Record<string, unknown>;
  }) {
    super(params.message);
    this.name = "ApiError";
    this.status = params.status;
    this.code = params.code;
    this.fieldErrors = params.fieldErrors ?? [];
    this.meta = params.meta;
  }
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== "object") return false;
  const maybe = (value as { error?: unknown }).error;
  return (
    !!maybe &&
    typeof maybe === "object" &&
    typeof (maybe as { code?: unknown }).code === "string" &&
    typeof (maybe as { message?: unknown }).message === "string"
  );
}

/**
 * axios 에러를 docs/04-api-spec.md §2.3 envelope 기준 `ApiError`로 정규화한다.
 * envelope 형식이 아닌 경우 status 기반 fallback 메시지를 사용한다.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<unknown>;
    const status = axiosError.response?.status ?? 0;
    const body = axiosError.response?.data;
    if (isApiErrorBody(body)) {
      return new ApiError({
        status,
        code: body.error.code,
        message: body.error.message,
        fieldErrors: body.error.fieldErrors,
        meta: body.meta,
      });
    }
    return new ApiError({
      status,
      code: status === 0 ? "NETWORK_ERROR" : "UNKNOWN",
      message:
        status === 0
          ? "서버에 연결할 수 없습니다."
          : `요청 처리 중 오류가 발생했습니다. (HTTP ${status})`,
    });
  }
  return new ApiError({
    status: 0,
    code: "UNKNOWN",
    message: "알 수 없는 오류가 발생했습니다.",
  });
}
