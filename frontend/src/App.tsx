import {
  Suspense,
  lazy,
  useEffect,
  type ReactNode,
} from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AppShell from "@/components/layout/AppShell";
import RequireAuth from "@/components/auth/RequireAuth";
import { Toaster } from "@/components/ui/sonner";
import { StrategyInstanceSelectionProvider } from "@/context/StrategyInstanceSelectionProvider";
import DashboardPage from "@/pages/DashboardPage";
import { ensureCsrfToken } from "@/lib/api";

/**
 * 라우트 구성 — docs/spec2.md §6.2.
 *
 * Phase 1 real:
 *  - `/login`
 *  - `/` 대시보드 (비로그인 허용)
 *
 * Phase 1 stub (Phase 2에서 본 구현):
 *  - `/trades`, `/news`
 *
 * 인증 필요:
 *  - `/settings` (RequireAuth → /login?next=/settings)
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

export default function App() {
  // 앱 시작 시 CSRF 토큰을 한 번 발급받아 둔다.
  // (docs/04-api-spec.md §3.4 + docs/06-auth-security.md §5)
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
                path="settings"
                element={
                  <RequireAuth>
                    <Navigate to="/settings/instances" replace />
                  </RequireAuth>
                }
              />
              <Route
                path="settings/templates"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyTemplatesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/instances"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <StrategyInstancesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/instances/:id/prompt-versions"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <PromptVersionsPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/instances/:id/watchlist"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <WatchlistPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/models"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <ModelProfilesPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/accounts"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <BrokerAccountsPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/assets"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <AssetMasterPage />
                    </LazyRoute>
                  </RequireAuth>
                }
              />
              <Route
                path="settings/system-parameters"
                element={
                  <RequireAuth>
                    <LazyRoute>
                      <SystemParametersPage />
                    </LazyRoute>
                  </RequireAuth>
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
