/**
 * docs/04-api-spec.md 응답 형식과 도메인 타입 정의.
 */

export interface ApiEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface ApiPagedMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiPaged<T> {
  data: T[];
  meta: ApiPagedMeta;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    fieldErrors?: ApiFieldError[];
  };
  meta?: Record<string, unknown>;
}

/**
 * 잘 알려진 에러 코드(docs/04-api-spec.md §2.3). 알려지지 않은 코드는 string fallback.
 */
export type ApiErrorCode =
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "TOO_MANY_REQUESTS"
  | "NOT_FOUND"
  | "VALIDATION_ERROR"
  | "OPTIMISTIC_LOCK_CONFLICT"
  | "INSTANCE_NOT_ACTIVATABLE"
  | "BROKER_ACCOUNT_ALREADY_IN_USE"
  | (string & {});

// ─────────── auth (§3) ───────────

export interface AuthUser {
  id: string;
  loginId: string;
  displayName: string;
  roleCode: string;
}

export interface LoginRequest {
  loginId: string;
  password: string;
}

export interface LoginResponse {
  expiresAt: string;
  user: AuthUser;
}

// ─────────── dashboard (§4) ───────────

export type ExecutionMode = "paper" | "live";
export type LifecycleState = "draft" | "active" | "inactive";
export type AutoPausedReason = "reconcile_failed" | null;
export type CycleStatus = "EXECUTE" | "HOLD" | "FAILED";
export type SystemStatusCode = "ok" | "delayed" | "down" | "unknown";

export interface StrategyOverviewCard {
  strategyInstanceId: string;
  name: string;
  executionMode: ExecutionMode;
  lifecycleState: LifecycleState;
  autoPausedReason: AutoPausedReason;
  budgetAmount: number;
  cashAmount: number;
  totalAssetAmount: number;
  todayRealizedPnl: number;
  latestDecisionStatus: CycleStatus | null;
  latestDecisionAt: string | null;
  watchlistCount: number;
}

export interface SystemStatusItem {
  serviceName: string;
  statusCode: SystemStatusCode;
  message: string | null;
  lastSuccessAt: string | null;
}

export interface InstanceDashboardInstance {
  id: string;
  name: string;
  executionMode: ExecutionMode;
  lifecycleState: LifecycleState;
  autoPausedReason: AutoPausedReason;
  budgetAmount: number;
  brokerAccountMasked: string | null;
}

export interface InstancePortfolio {
  cashAmount: number;
  totalAssetAmount: number;
  realizedPnlToday: number;
}

export interface InstancePosition {
  symbolCode: string;
  symbolName: string;
  quantity: number;
  avgBuyPrice: number;
  lastMarkPrice: number;
  unrealizedPnl: number;
}

export interface InstanceLatestDecision {
  decisionLogId: string;
  cycleStatus: CycleStatus;
  summary: string | null;
  cycleStartedAt: string;
}

export interface InstanceRecentOrder {
  tradeOrderId: string;
  symbolCode: string;
  side: "BUY" | "SELL";
  orderStatus: string;
  requestedAt: string;
  requestedQuantity: number;
  requestedPrice: number | null;
}

export interface InstanceDashboardSystemStatus {
  serviceName: string;
  statusCode: SystemStatusCode;
  message: string | null;
  occurredAt: string | null;
}

export interface InstanceDashboard {
  instance: InstanceDashboardInstance;
  portfolio: InstancePortfolio;
  positions: InstancePosition[];
  systemStatus: InstanceDashboardSystemStatus[];
  latestDecision: InstanceLatestDecision | null;
  recentOrders: InstanceRecentOrder[];
}

// ─────────── 매매이력 (§5) ───────────

export type TradeSide = "BUY" | "SELL" | (string & {});
export type TradeOrderStatus =
  | "requested"
  | "accepted"
  | "partial"
  | "filled"
  | "canceled"
  | "rejected"
  | "failed"
  | (string & {});

export interface TradeOrderListFilter {
  strategyInstanceId?: string | null;
  symbolCode?: string;
  orderStatus?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

export interface TradeOrderListItem {
  id: string;
  strategyInstanceId: string;
  instanceName: string;
  symbolCode: string;
  side: TradeSide;
  executionMode: ExecutionMode;
  orderStatus: TradeOrderStatus;
  requestedQuantity: number;
  requestedPrice: number | null;
  filledQuantity: number | null;
  avgFilledPrice: number | null;
  requestedAt: string;
  filledAt: string | null;
}

export interface TradeOrderDetail {
  id: string;
  tradeOrderIntentId: string;
  strategyInstanceId: string;
  clientOrderId: string;
  brokerOrderNo: string | null;
  executionMode: ExecutionMode;
  orderStatus: TradeOrderStatus;
  requestedQuantity: number;
  requestedPrice: number | null;
  filledQuantity: number | null;
  avgFilledPrice: number | null;
  requestedAt: string;
  acceptedAt: string | null;
  filledAt: string | null;
  failureReason: string | null;
  portfolioAfter: unknown;
}

export interface TradeDecisionListFilter {
  strategyInstanceId?: string | null;
  cycleStatus?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

export interface TradeDecisionListItem {
  id: string;
  strategyInstanceId: string;
  instanceName: string;
  cycleStatus: CycleStatus;
  summary: string | null;
  confidence: number | null;
  failureReason: string | null;
  cycleStartedAt: string;
  cycleFinishedAt: string | null;
  orderCount: number;
}

export interface OrderIntentView {
  id: string;
  sequenceNo: number;
  symbolCode: string;
  side: TradeSide;
  quantity: number;
  orderType: string;
  price: number | null;
  rationale: string | null;
  evidence: unknown;
  executionBlockedReason: string | null;
}

export interface OrderRefView {
  id: string;
  tradeOrderIntentId: string;
  orderStatus: TradeOrderStatus;
  requestedAt: string;
}

export interface TradeDecisionDetail {
  id: string;
  strategyInstanceId: string;
  instanceName: string;
  cycleStatus: CycleStatus;
  summary: string | null;
  confidence: number | null;
  failureReason: string | null;
  failureDetail: string | null;
  cycleStartedAt: string;
  cycleFinishedAt: string | null;
  requestText: string | null;
  responseText: string | null;
  stdoutText: string | null;
  stderrText: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  estimatedCost: number | null;
  callStatus: string | null;
  engineName: string | null;
  modelName: string | null;
  parsedDecision: unknown;
  settingsSnapshot: unknown;
  orderIntents: OrderIntentView[];
  orders: OrderRefView[];
}

// ─────────── 뉴스·공시 (§6) ───────────

export type NewsUsefulnessStatus =
  | "useful"
  | "not_useful"
  | "unclassified"
  | (string & {});

export interface NewsRelatedAsset {
  symbolCode: string;
  symbolName: string | null;
}

export interface NewsListFilter {
  symbolCode?: string;
  strategyInstanceId?: string | null;
  usefulnessStatus?: NewsUsefulnessStatus | string;
  dateFrom?: string;
  dateTo?: string;
  q?: string;
  page?: number;
  size?: number;
}

export interface NewsListItem {
  id: string;
  providerName: string;
  title: string;
  articleUrl: string;
  publishedAt: string;
  summary: string | null;
  usefulnessStatus: NewsUsefulnessStatus;
  relatedAssets: NewsRelatedAsset[];
}

export interface DisclosureListFilter {
  symbolCode?: string;
  dartCorpCode?: string;
  strategyInstanceId?: string | null;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

export interface DisclosureListItem {
  id: string;
  dartCorpCode: string;
  symbolCode: string | null;
  symbolName: string | null;
  title: string;
  publishedAt: string;
  previewText: string | null;
  documentUrl: string;
}

// ─────────── 차트 (§7) ───────────

export interface MinuteBar {
  barTime: string;
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  volume: number;
}

export interface MinuteBarsResponse {
  symbolCode: string;
  dateFrom: string;
  dateTo: string;
  bars: MinuteBar[];
}

export interface ChartMinuteBarsFilter {
  symbolCode: string;
  dateFrom: string;
  dateTo: string;
}

export interface ChartOrderOverlayFilter extends ChartMinuteBarsFilter {
  strategyInstanceId?: string | null;
}

export interface ChartOrderOverlay {
  tradeOrderId: string;
  side: TradeSide;
  orderStatus: TradeOrderStatus;
  requestedAt: string;
  filledAt: string | null;
  requestedPrice: number | null;
  avgFilledPrice: number | null;
  requestedQuantity: number;
}

// ─────────── 설정 API 공통 (§8) ───────────

/**
 * 입력 스펙 / 실행 설정의 JSON 본문은 백엔드가 정의한 자유 schema 다.
 * Phase 1.5 에서는 시드용 CRUD 만 다루므로 운영자에게는 JSON 텍스트로 노출하고,
 * 클라이언트에서는 그대로 unknown record 로 통과시킨다.
 */
export type JsonRecord = Record<string, unknown>;

// ─────────── 전략 템플릿 §8.1~8.3 ───────────

export interface StrategyTemplate {
  id: string;
  name: string;
  description: string | null;
  defaultCycleMinutes: number;
  defaultPromptText?: string | null;
  defaultExecutionConfig?: JsonRecord;
  defaultTradingModelProfileId: string | null;
  version: number;
  updatedAt: string;
}

export interface StrategyTemplateCreateRequest {
  name: string;
  description?: string | null;
  defaultCycleMinutes: number;
  defaultPromptText?: string;
  defaultExecutionConfig?: JsonRecord;
  defaultTradingModelProfileId?: string | null;
}

export interface StrategyTemplateUpdateRequest {
  name: string;
  description?: string | null;
  defaultCycleMinutes: number;
  defaultPromptText?: string;
  defaultExecutionConfig?: JsonRecord;
  defaultTradingModelProfileId?: string | null;
  version: number;
}

// ─────────── 전략 인스턴스 §8.4~8.8 ───────────

export interface StrategyInstance {
  id: string;
  strategyTemplateId: string;
  strategyTemplateName: string;
  name: string;
  executionMode: ExecutionMode;
  lifecycleState: LifecycleState;
  autoPausedReason: AutoPausedReason;
  brokerAccountId: string | null;
  brokerAccountMasked: string | null;
  budgetAmount: number;
  currentPromptVersionId: string | null;
  tradingModelProfileId: string | null;
  cycleMinutes: number | null;
  effectiveCycleMinutes: number;
  executionConfigOverride: JsonRecord | null;
  autoPausedAt: string | null;
  version: number;
  updatedAt: string;
}

export interface StrategyInstanceCreateRequest {
  strategyTemplateId: string;
  name: string;
  executionMode: ExecutionMode;
  brokerAccountId: string | null;
  budgetAmount: number;
  tradingModelProfileId: string | null;
  cycleMinutes?: number | null;
  executionConfigOverride?: JsonRecord | null;
}

export interface StrategyInstanceUpdateRequest {
  name?: string;
  executionMode?: ExecutionMode;
  budgetAmount?: number;
  brokerAccountId?: string | null;
  tradingModelProfileId?: string | null;
  cycleMinutes?: number | null;
  executionConfigOverride?: JsonRecord | null;
  version: number;
}

export interface StrategyInstanceLifecycleRequest {
  targetState: LifecycleState;
  version: number;
}

export interface StrategyInstanceDuplicateRequest {
  name: string;
}

export interface StrategyInstanceListFilter {
  lifecycleState?: LifecycleState;
  executionMode?: ExecutionMode;
}

// ─────────── 프롬프트 버전 §8.9~8.11 ───────────

export interface PromptVersion {
  id: string;
  versionNo: number;
  promptText: string;
  changeNote: string | null;
  createdBy: string | null;
  current: boolean;
  createdAt: string;
}

export interface PromptVersionCreateRequest {
  promptText: string;
  changeNote?: string | null;
}

export interface PromptVersionActivateRequest {
  version: number;
}

// ─────────── 감시 종목 §8.12~8.14 ───────────

export interface WatchlistItem {
  assetMasterId: string;
  symbolCode: string;
  symbolName: string | null;
  addedAt: string;
}

export interface WatchlistAddRequest {
  assetMasterId: string;
}

// ─────────── 모델 프로필 §8.15~8.16 ───────────

export type ModelPurpose = "trading" | "report" | "news" | (string & {});

export interface ModelProfile {
  id: string;
  name: string;
  purpose: ModelPurpose;
  provider: string;
  modelName: string;
  enabled: boolean;
  parameters?: JsonRecord;
  version: number;
  updatedAt: string;
}

export interface ModelProfileCreateRequest {
  name: string;
  purpose: ModelPurpose;
  provider: string;
  modelName: string;
  enabled: boolean;
  parameters?: JsonRecord;
}

export interface ModelProfileUpdateRequest extends ModelProfileCreateRequest {
  version: number;
}

export interface ModelProfileListFilter {
  purpose?: ModelPurpose;
  enabled?: boolean;
}

// ─────────── 브로커 계좌 §8.17~8.18 ───────────

export interface BrokerAccount {
  id: string;
  alias: string;
  brokerCode: string;
  accountNumberMasked: string;
  enabled: boolean;
  version: number;
  updatedAt: string;
}

export interface BrokerAccountCreateRequest {
  alias: string;
  brokerCode: string;
  accountNumber: string;
  apiKey?: string;
  apiSecret?: string;
  enabled: boolean;
}

export interface BrokerAccountUpdateRequest {
  alias: string;
  brokerCode: string;
  accountNumber?: string;
  apiKey?: string;
  apiSecret?: string;
  enabled: boolean;
  version: number;
}

// ─────────── 글로벌 종목 마스터 §8.19~8.20 ───────────

export interface AssetMaster {
  id: string;
  symbolCode: string;
  symbolName: string;
  marketType: string;
  dartCorpCode: string | null;
  hidden: boolean;
  version: number;
  updatedAt: string;
}

export interface AssetMasterCreateRequest {
  symbolCode: string;
  symbolName: string;
  marketType: string;
  dartCorpCode?: string;
  hidden: boolean;
}

export interface AssetMasterUpdateRequest extends AssetMasterCreateRequest {
  version: number;
}

export interface AssetMasterListFilter {
  q?: string;
  hidden?: boolean;
}

export interface DartCorpCodeLookupResult {
  assetMasterId: string;
  symbolCode: string;
  symbolName: string;
  dartCorpCode: string | null;
  hidden: boolean;
}

// ─────────── paper 평가 (paper-eval) — 박스 단타 v1 정직성 보강 ───────────

/**
 * 백엔드 응답의 비율(BigDecimal) 값. Jackson 기본 직렬화는 number 지만,
 * BigDecimal 정밀도를 보존하려고 string 으로 내릴 경우도 대비해 둘 다 허용한다.
 *
 * 본 프로젝트 ObjectMapper 기본 설정에서는 number 로 직렬화된다.
 */
export type ApiDecimal = number | string;

export interface PaperEvalMetricSnapshot {
  tradesCount: number;
  wins: number;
  losses: number;
  /** 0~1 범위 (예: 0.5333 = 53.33%) */
  hitRate: ApiDecimal;
  /** % 단위 (예: 0.0123 = +1.23%) — net_pnl_pct 합 */
  sumProfitPct: ApiDecimal;
  /** % 단위 (음수) — net_pnl_pct 합 */
  sumLossPct: ApiDecimal;
  /** % 단위 — 평균 net_pnl_pct */
  ev: ApiDecimal;
  /** Profit Factor. 손실 없으면 null */
  pf: ApiDecimal | null;
  /** % 단위 — 누적 net_pnl_pct 의 max drawdown */
  mdd: ApiDecimal;
  /** % 단위. signed (음수면 운 좋음). 매치 없으면 null */
  avgSlippageBuyPct: ApiDecimal | null;
  avgSlippageSellPct: ApiDecimal | null;
  avgSellTaxPct: ApiDecimal | null;
  avgFeePct: ApiDecimal | null;
  /** % 단위 — |slip_buy| + |slip_sell| + sell_tax + fee 의 평균 합 */
  avgCostTotalPct: ApiDecimal;
}

export interface PaperEvalDailyPoint {
  /** ISO `YYYY-MM-DD` (KST 기준) */
  businessDate: string;
  /** 해당 일 매매 net_pnl_pct 합 */
  netPnlPct: ApiDecimal;
}

export interface PaperEvalRecentMatch {
  id: string;
  symbolCode: string;
  /** ISO-8601 with offset */
  entryTime: string;
  exitTime: string;
  holdingMinutes: number;
  matchedQuantity: ApiDecimal;
  grossPnlPct: ApiDecimal;
  netPnlPct: ApiDecimal;
  slippageBuyPct: ApiDecimal | null;
  slippageSellPct: ApiDecimal | null;
  sellTaxPct: ApiDecimal | null;
  feePct: ApiDecimal | null;
}

// ─────────── data-collection 관리 — F2 ───────────

/**
 * 백엔드 CollectionDashboardService.OpsEventView 매핑.
 * - statusCode: "ok" | "down" | "limit_exceeded" (그 외 string 도 가능)
 */
export interface OpsEventView {
  id: string;
  /** ISO-8601 */
  occurredAt: string;
  serviceName: string;
  eventType: string;
  /** 잘 알려진 값: "ok" | "down" | "limit_exceeded" */
  statusCode: string;
  message: string | null;
  /** 백엔드가 JsonNode.toString() 으로 직렬화. null 가능 */
  payloadJson: string | null;
}

/** 통합 상태 — frontend 가 색상 매핑에 사용 */
export type DataCollectionStatus =
  | "ok"
  | "delayed"
  | "down"
  | "unknown"
  | (string & {});

export interface DataCollectionWsSection {
  status: DataCollectionStatus;
  /** WebSocketStatusTracker.WebSocketState.name() */
  connectionState: "CONNECTED" | "DELAYED" | "DISCONNECTED" | "UNKNOWN" | (string & {});
  lastTickAt: string | null;
  lastConnectedAt: string | null;
  lastDisconnectedAt: string | null;
  subscribedCount: number;
  maxSubscriptions: number;
  adhocCount: number;
  count24hOk: number;
  count24hDown: number;
  count24hLimitExceeded: number;
  latestEvent: OpsEventView | null;
}

export interface DataCollectionRestSection {
  status: DataCollectionStatus;
  lastSnapshotAt: string | null;
  lastMinuteBarAt: string | null;
}

export interface DataCollectionContentSection {
  status: DataCollectionStatus;
  lastNewsAt: string | null;
  lastDisclosureAt: string | null;
  newsCount24hOk: number;
  newsCount24hDown: number;
  disclosureCount24hOk: number;
  disclosureCount24hDown: number;
  latestEvent: OpsEventView | null;
}

export interface DataCollectionMacroSection {
  status: DataCollectionStatus;
  /** ISO `YYYY-MM-DD` */
  lastBaseDate: string | null;
  count24hOk: number;
  count24hDown: number;
  latestEvent: OpsEventView | null;
}

export interface DataCollectionSummary {
  evaluatedAt: string;
  ws: DataCollectionWsSection;
  rest: DataCollectionRestSection;
  content: DataCollectionContentSection;
  macro: DataCollectionMacroSection;
}

export interface WsSubscriptionRow {
  symbolCode: string;
  /** "watchlist" | "adhoc" | "adhoc_pending" */
  source: "watchlist" | "adhoc" | "adhoc_pending" | (string & {});
}

export interface WsSubscriptionList {
  rows: WsSubscriptionRow[];
  totalCount: number;
  maxSubscriptions: number;
  connectionState: string;
  lastTickAt: string | null;
}

export interface WsSubscribeRequest {
  symbolCodes: string[];
}

export interface WsSubscribeResponse {
  addedCount: number;
  requestedCount: number;
}

export interface WsUnsubscribeResponse {
  removed: boolean;
  symbolCode: string;
}

export type OpsEventService = "marketdata" | "news" | "disclosure" | "macro";

// ─────────── trade-history (F3) ───────────

export type TradeHistorySortField =
  | "exit_time"
  | "net_pnl_pct"
  | "gross_pnl_pct";
export type TradeHistorySortDir = "asc" | "desc";
export type TradeHistorySortSpec = `${TradeHistorySortField}:${TradeHistorySortDir}`;

export interface TradeHistoryFilter {
  page?: number;
  size?: number;
  /** ISO-8601 with offset */
  from?: string | null;
  to?: string | null;
  symbol?: string | null;
  winOnly?: boolean;
  lossOnly?: boolean;
  sort?: TradeHistorySortSpec;
}

export interface TradeHistoryRow {
  matchId: string;
  symbolCode: string;
  entryTime: string;
  exitTime: string;
  holdingMinutes: number;
  matchedQuantity: ApiDecimal;
  grossPnlPct: ApiDecimal;
  netPnlPct: ApiDecimal;
  // V18 match-level pct (signed for slippage)
  slippageBuyPct: ApiDecimal | null;
  slippageSellPct: ApiDecimal | null;
  sellTaxPct: ApiDecimal | null;
  feePct: ApiDecimal | null;
  // V17 buy
  buyOrderId: string | null;
  buyRequestedPrice: ApiDecimal | null;
  buyAvgFilledPrice: ApiDecimal | null;
  buyRequestedAmount: ApiDecimal | null;
  buySlippageAmount: ApiDecimal | null;
  buyCommissionAmount: ApiDecimal | null;
  buyActualAmount: ApiDecimal | null;
  buyWalkLevels: number | null;
  buyPartialFillRatio: ApiDecimal | null;
  // V17 sell
  sellOrderId: string | null;
  sellRequestedPrice: ApiDecimal | null;
  sellAvgFilledPrice: ApiDecimal | null;
  sellRequestedAmount: ApiDecimal | null;
  sellSlippageAmount: ApiDecimal | null;
  sellSellTaxAmount: ApiDecimal | null;
  sellCommissionAmount: ApiDecimal | null;
  sellActualAmount: ApiDecimal | null;
  sellWalkLevels: number | null;
  sellPartialFillRatio: ApiDecimal | null;
}

export interface TradeHistorySummaryView {
  tradesCount: number;
  winCount: number;
  lossCount: number;
  sumNetPnlPct: ApiDecimal;
}

export interface TradeHistoryResult {
  rows: TradeHistoryRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  summary: TradeHistorySummaryView;
}

// ─────────── audit-log (F4) ───────────

export interface AuditLogFilter {
  page?: number;
  size?: number;
  from?: string | null;
  to?: string | null;
  targetType?: string | null;
  actorType?: string | null;
  actionType?: string | null;
}

export interface AuditLogRow {
  id: string;
  occurredAt: string;
  actorType: string;
  actorId: string | null;
  targetType: string;
  targetId: string | null;
  actionType: string;
  beforeJson: string | null;
  afterJson: string | null;
  summaryJson: string | null;
}

export interface AuditLogPageResult {
  rows: AuditLogRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ─────────── 시스템 파라미터 §8.21 ───────────

export interface SystemParameter {
  parameterKey: string;
  valueJson: unknown;
  description: string | null;
  version: number;
  updatedAt: string;
}

export interface SystemParameterCreateRequest {
  parameterKey: string;
  valueJson: unknown;
  description?: string | null;
}

export interface SystemParameterUpdateRequest {
  valueJson: unknown;
  description?: string | null;
  version: number;
}
