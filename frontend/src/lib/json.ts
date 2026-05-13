import type { JsonRecord } from "./api-types";

/**
 * 운영자 JSON 입력 텍스트 → object 변환.
 *
 * - 빈 문자열은 `{}` 로 간주한다.
 * - parse 실패 시 throw 한다 (호출 측에서 zod / form 에서 catch).
 * - 최상위가 객체가 아닌 값은 reject 한다.
 */
export function parseJsonRecord(input: string): JsonRecord {
  const trimmed = input.trim();
  if (!trimmed) {
    return {};
  }
  const parsed: unknown = JSON.parse(trimmed);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("JSON 객체 형식이어야 합니다.");
  }
  return parsed as JsonRecord;
}

export function stringifyJsonRecord(value: JsonRecord | undefined): string {
  if (!value || Object.keys(value).length === 0) {
    return "";
  }
  return JSON.stringify(value, null, 2);
}
