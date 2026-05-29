import {
  Suspense,
  lazy,
  useEffect,
  type ReactNode,
} from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes, useParams } from "react-router-dom";
import AppShell from "@/components/layout/AppShell";
import RequireAuth from "@/components/auth/RequireAuth";
import { Toaster } from "@/components/ui/sonner";
import { StrategyInstanceSelectionProvider } from "@/context/StrategyInstanceSelectionProvider";
import DashboardPage from "@/pages/DashboardPage";
import { ensureCsrfToken } from "@/lib/api";

/**
 * 라우트 구성 — F6 재구성 후.
 *
 * 조회/대시보드 (글로벌 path, 페이지 내부 인스턴스 selector):
 *  - /                대시보드
 *  - /chart           차트 (종목 + 인스턴스 selector)
 *  - /news            뉴스/공시
 *  - /trades          매매·판단 (인스턴스 selector)
 *  - /trade-history   매매 이력 (인스턴스 selector)
 *  - /paper-eval      paper 평가 (인스턴스 selector)
 *  - /portfolio       포트폴리오 (인스턴스 selector)
 *
 * 전략 관리 (`/strategy/*`):
 *  - /strategy                  인스턴스 목록
 *  - /strategy/:id/overview / settings / prompt / watchlist
 *
 * 관리 (`/admin/*`):
 *  - /admin/data-collection / asset-master / llm-models / system-parameters
 *  - /admin/audit-log / strategy-templates / broker-accounts
 *
 * Legacy redirect (북마크 호환):
 *  - F4 path `/strategy/:id/{paper-eval, trade-history, portfolio, trades}` → 글로벌 path + `?instanceId=:id`
 *  - F0 `/settings/*` → 신규 path
 */

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // spec §7.1 — 대시보드 30초 폴링과 정합
      staleTime: 30 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const ChartPage = lazy(() => import("@/pages/ChartPage"));
const LoginPage = lazy(() => import("@/pages/LoginPage"));
const NewsPage = lazy(() => import("@/pages/NewsPage"));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"));
const TradesPage = lazy(() => import("@/pages/TradesPage"));
const StrategyTemplatesPage = lazy(
  () => import("@/pages/settings/StrategyTemplatesPage"),
);
const StrategyInstancesPage = lazy(
  () => import("@/pages/settings/StrategyInstancesPage"),
);
const PromptVersionsPage = lazy(
  () => import("@/pages/settings/PromptVersionsPage"),
);
const WatchlistPage = lazy(() => import("@/pages/settings/WatchlistPage"));
const PaperEvalPage = lazy(() => import("@/pages/settings/PaperEvalPage"));
const DataCollectionPage = lazy(
  () => import("@/pages/settings/DataCollectionPage"),
);
const TradeHistoryPage = lazy(
  () => import("@/pages/settings/TradeHistoryPage"),
);
const ModelProfilesPage = lazy(
  () => import("@/pages/settings/ModelProfilesPage"),
);
const BrokerAccountsPage = lazy(
  () => import("@/pages/settings/BrokerAccountsPage"),
);
const AssetMasterPage = lazy(() => import("@/pages/settings/AssetMasterPage"));
const SystemParametersPage = lazy(
  () => import("@/pages/settings/SystemParametersPage"),
);
const AuditLogPage = lazy(() => import("@/pages/admin/AuditLogPage"));
const StrategyOverviewPage = lazy(
  () => import("@/pages/strategy/StrategyOverviewPage"),
);
const StrategyPortfolioPage = lazy(
  () => import("@/pages/strategy/StrategyPortfolioPage"),
);

function LazyRoute({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<RouteLoadingFallback />}>
      {children}
    </Suspense>
  );
}

function RouteLoadingFallback() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center py-16 text-sm text-muted-foreground">
      페이지를 불러오는 중...
    </div>
  );
}

/**
 * 기존 `/settings/instances/:id/<sub>` → `/strategy/:id/<sub>` redirect (F4 호환).
 */
function RedirectInstanceSubpathToStrategy({
  subpath,
}: {
  subpath: string;
}) {
  const params = useParams();
  return <Navigate to={`/strategy/${params.id}/${subpath}`} replace />;
}

/**
 * F6 — `/strategy/:id/<sub>` 또는 `/settings/instances/:id/<sub>` 의 일부 (조회 메뉴) 를
 * 글로벌 path + `?instanceId=:id` 로 redirect.
 *
 * 예: `/strategy/abc/paper-eval` → `/paper-eval?instanceId=abc`
 */
function RedirectInstanceSubpathToGlobal({
  globalPath,
}: {
  globalPath: string;
}) {
  const params = useParams();
  return (
    <Navigate
      to={`${globalPath}?instanceId=${encodeURIComponent(params.id ?? "")}`}
      replace
    />
  );
}

export default function App() {
  // 앱 시작 시 CSRF 토큰을 한 번 발급받아 둔다.
  useEffect(() => {
    ensureCsrfToken().catch((error: unknown) => {
      console.error("[alt] CSRF 토큰 초기화 실패", error);
    });
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <StrategyInstanceSelectionProvider>
        <BrowserRouter>
          <Routes>
            <Route
              path="/login"
              element={
                <LazyRoute>
                  <LoginPage />
                </LazyRoute>
              }
            />
            <Route element={<AppShell />}>
              {/* ── 조회/대시보드 (글로벌 path) ── */}
              <Route index element={<DashboardPage />} />
              <Route
                path="trades"
                element={
                  <LazyRoute>
                    <TradesPage />
                  </LazyRoute>
                }
              />
              <Route
                path="news"
                element={
                  <LazyRoute>
                    <NewsPage />
                  </LazyRoute>
                }
              />
              <Route
                path="chart"
                element={
                  <LazyRoute>
                    <ChartPage />
                  </LazyRoute>
                }
              />
              <Route
                path="paper-eval"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <PaperEvalPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="trade-history"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <TradeHistoryPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="portfolio"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyPortfolioPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />

              {/* ── /admin/* ── */}
              <Route
                path="admin"
                element={
                  <RequireAuth>
                    <Navigate to="/admin/data-collection" replace />
                  </RequireAuth>
                }
              />
              <Route
                path="admin/data-collection"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <DataCollectionPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="admin/asset-master"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <AssetMasterPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="admin/llm-models"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <ModelProfilesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="admin/system-parameters"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <SystemParametersPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="admin/audit-log"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <AuditLogPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="admin/strategy-templates"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyTemplatesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="admin/broker-accounts"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <BrokerAccountsPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />

              {/* ── /strategy/* (전략 관리) ── */}
              <Route
                path="strategy"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyInstancesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id"
                element={<Navigate to="overview" replace />}
              />
              <Route
                path="strategy/:id/overview"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyOverviewPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id/watchlist"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <WatchlistPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id/prompt"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <PromptVersionsPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id/settings"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyInstancesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />

              {/* ── F4 → F6 redirect (조회 메뉴는 글로벌 path 로 이동, 북마크 호환) ── */}
              <Route
                path="strategy/:id/paper-eval"
                element={
                  <RedirectInstanceSubpathToGlobal globalPath="/paper-eval" />
                }
              />
              <Route
                path="strategy/:id/trade-history"
                element={
                  <RedirectInstanceSubpathToGlobal globalPath="/trade-history" />
                }
              />
              <Route
                path="strategy/:id/portfolio"
                element={
                  <RedirectInstanceSubpathToGlobal globalPath="/portfolio" />
                }
              />
              <Route
                path="strategy/:id/trades"
                element={
                  <RedirectInstanceSubpathToGlobal globalPath="/trades" />
                }
              />

              {/* ── F0 `/settings/*` → 신규 path redirect (북마크 호환) ── */}
              <Route
                path="settings"
                element={<Navigate to="/strategy" replace />}
              />
              <Route
                path="settings/instances"
                element={<Navigate to="/strategy" replace />}
              />
              <Route
                path="settings/templates"
                element={
                  <Navigate to="/admin/strategy-templates" replace />
                }
              />
              <Route
                path="settings/assets"
                element={<Navigate to="/admin/asset-master" replace />}
              />
              <Route
                path="settings/models"
                element={<Navigate to="/admin/llm-models" replace />}
              />
              <Route
                path="settings/accounts"
                element={<Navigate to="/admin/broker-accounts" replace />}
              />
              <Route
                path="settings/system-parameters"
                element={
                  <Navigate to="/admin/system-parameters" replace />
                }
              />
              <Route
                path="settings/data-collection"
                element={<Navigate to="/admin/data-collection" replace />}
              />
              <Route
                path="settings/instances/:id/prompt-versions"
                element={
                  <RedirectInstanceSubpathToStrategy subpath="prompt" />
                }
              />
              <Route
                path="settings/instances/:id/watchlist"
                element={
                  <RedirectInstanceSubpathToStrategy subpath="watchlist" />
                }
              />
              <Route
                path="settings/instances/:id/paper-eval"
                element={
                  <RedirectInstanceSubpathToGlobal globalPath="/paper-eval" />
                }
              />
              <Route
                path="settings/instances/:id/trade-history"
                element={
                  <RedirectInstanceSubpathToGlobal globalPath="/trade-history" />
                }
              />

              <Route
                path="*"
                element={
                  <LazyRoute>
                    <NotFoundPage />
                  </LazyRoute>
                }
              />
            </Route>
          </Routes>
        </BrowserRouter>
      </StrategyInstanceSelectionProvider>
      <Toaster position="top-right" />
    </QueryClientProvider>
  );
}
