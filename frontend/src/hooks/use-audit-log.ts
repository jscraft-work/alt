import { useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiEnvelope,
  AuditLogFilter,
  AuditLogPageResult,
} from "@/lib/api-types";

/**
 * F4 — audit_log read-only 페이지네이션 조회.
 * 백엔드: AuditLogAdminController.GET /api/admin/audit-log
 */

export const AUDIT_LOG_BASE_KEY = ["admin", "audit-log"] as const;

export const auditLogKey = (filter: AuditLogFilter) =>
  [
    ...AUDIT_LOG_BASE_KEY,
    filter.page ?? 0,
    filter.size ?? 50,
    filter.from ?? null,
    filter.to ?? null,
    (filter.targetType ?? "").trim() || null,
    (filter.actorType ?? "").trim() || null,
    (filter.actionType ?? "").trim() || null,
  ] as const;

async function fetchAuditLog(
  filter: AuditLogFilter,
): Promise<AuditLogPageResult> {
  const params: Record<string, string | number> = {
    page: filter.page ?? 0,
    size: filter.size ?? 50,
  };
  if (filter.from) params.from = filter.from;
  if (filter.to) params.to = filter.to;
  if (filter.targetType && filter.targetType.trim()) {
    params.targetType = filter.targetType.trim();
  }
  if (filter.actorType && filter.actorType.trim()) {
    params.actorType = filter.actorType.trim();
  }
  if (filter.actionType && filter.actionType.trim()) {
    params.actionType = filter.actionType.trim();
  }
  try {
    const res = await api.get<ApiEnvelope<AuditLogPageResult>>(
      "/admin/audit-log",
      { params },
    );
    return res.data.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useAuditLog(filter: AuditLogFilter) {
  return useQuery<AuditLogPageResult, ApiError>({
    queryKey: auditLogKey(filter),
    queryFn: () => fetchAuditLog(filter),
    placeholderData: (prev) => prev,
  });
}
