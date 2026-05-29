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
 * 라우트 구성 — F4 재구성 후.
 *
 * 글로벌 path (`/admin/*`):
 *  - /admin/data-collection, /admin/asset-master, /admin/llm-models, /admin/system-parameters
 *  - /admin/audit-log (신규), /admin/strategy-templates, /admin/broker-accounts
 *
 * 전략별 path (`/strategy/*`):
 *  - /strategy (인스턴스 목록), /strategy/:id/overview / paper-eval / trade-history / portfolio / trades / watchlist / prompt / settings
 *
 * 기존 `/settings/*` path 는 모두 신규 path 로 redirect (북마크 호환).
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
 * 기존 `/settings/instances/:id/<sub>` → `/strategy/:id/<sub>` redirect.
 * sub 매핑은 F4 path 변경에 정합 — prompt-versions → prompt 등.
 */
function RedirectInstanceSubpath({ subpath }: { subpath: string }) {
  const params = useParams();
  return <Navigate to={`/strategy/${params.id}/${subpath}`} replace />;
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
              {/* ── 글로벌 (비인스턴스) ── */}
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

              {/* ── /strategy/* ── */}
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
                path="strategy/:id/paper-eval"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <PaperEvalPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id/trade-history"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <TradeHistoryPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id/portfolio"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyPortfolioPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="strategy/:id/trades"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <TradesPage />
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

              {/* ── 기존 /settings/* path → 새 path redirect (북마크 호환) ── */}
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
                element={<RedirectInstanceSubpath subpath="prompt" />}
              />
              <Route
                path="settings/instances/:id/watchlist"
                element={<RedirectInstanceSubpath subpath="watchlist" />}
              />
              <Route
                path="settings/instances/:id/paper-eval"
                element={<RedirectInstanceSubpath subpath="paper-eval" />}
              />
              <Route
                path="settings/instances/:id/trade-history"
                element={
                  <RedirectInstanceSubpath subpath="trade-history" />
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
