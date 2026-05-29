/**
 * 시간/숫자 포맷 유틸. spec §10.7에 따라 KST 기준으로 표시한다.
 */

const KRW_FORMATTER = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

const NUMBER_FORMATTER = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 0,
});

const PERCENT_FORMATTER = new Intl.NumberFormat("ko-KR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatKrw(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return KRW_FORMATTER.format(value);
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return NUMBER_FORMATTER.format(value);
}

export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return `${PERCENT_FORMATTER.format(value)}%`;
}

const SIGNED_PERCENT_FORMATTER = new Intl.NumberFormat("ko-KR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: "exceptZero",
});

const SIGNED_PERCENT_4_FORMATTER = new Intl.NumberFormat("ko-KR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
  signDisplay: "exceptZero",
});

/**
 * 백엔드의 BigDecimal 값(number | string)을 number 로 안전하게 정규화한다.
 * 파싱 실패 시 null.
 */
export function toFiniteNumber(value: unknown): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  const num = typeof value === "string" ? Number(value) : (value as number);
  return Number.isFinite(num) ? num : null;
}

/**
 * 0~1 비율(ratio)을 `XX.XX%` 로 표시한다. (예: 0.5333 → `53.33%`)
 */
export function formatRatioAsPercent(value: unknown): string {
  const num = toFiniteNumber(value);
  if (num === null) {
    return "-";
  }
  return `${PERCENT_FORMATTER.format(num * 100)}%`;
}

/**
 * 이미 `% 단위` 값을 그대로 `+X.XX%` / `-X.XX%` 로 표시한다.
 * (예: 0.0123 = 1.23% 가 아니라 0.0123% 로 들어옴 — paper_trade_match.net_pnl_pct 가 비율값 vs % 단위 인지에 따라 호출자가 선택)
 */
export function formatSignedPercent(value: unknown, digits: 2 | 4 = 2): string {
  const num = toFiniteNumber(value);
  if (num === null) {
    return "-";
  }
  const formatter =
    digits === 4 ? SIGNED_PERCENT_4_FORMATTER : SIGNED_PERCENT_FORMATTER;
  return `${formatter.format(num)}%`;
}

/**
 * BigDecimal 기반 % 값(예: net_pnl_pct = 0.0123 = +1.23%) 을 `+1.23%` 로 표시.
 * net_pnl_pct 는 `(sell.actual - buy.actual) / buy.actual` 의 ratio 라
 * 100 을 곱해야 한다.
 */
export function formatRatioAsSignedPercent(
  value: unknown,
  digits: 2 | 4 = 2,
): string {
  const num = toFiniteNumber(value);
  if (num === null) {
    return "-";
  }
  return formatSignedPercent(num * 100, digits);
}

/**
 * ISO-8601 (KST 기준 +09:00) 문자열을 `YYYY-MM-DD HH:mm KST`로 표시한다.
 */
export function formatKstDateTime(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const fmt = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = fmt.formatToParts(date);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")} ${get("hour")}:${get("minute")} KST`;
}
