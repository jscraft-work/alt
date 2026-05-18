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
  date: string;
  bars: MinuteBar[];
}

export interface ChartMinuteBarsFilter {
  symbolCode: string;
  date: string;
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
