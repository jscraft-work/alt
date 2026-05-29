import { useState } from "react";
import { Outlet } from "react-router-dom";
import Header from "./Header";
import Sidebar from "./Sidebar";
import { useRouteInstanceSync } from "@/hooks/use-route-instance-sync";

/**
 * 사이드바 + 헤더 공통 셸 (docs/spec2.md §6.1).
 *
 * - 데스크톱: 좌측 사이드바 고정, 우측에 헤더 + 메인 영역
 * - 모바일: 햄버거 메뉴로 사이드바 토글
 */
export default function AppShell() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  // F4 — URL 의 `/strategy/{id}/...` 와 StrategyInstanceSelectionProvider 동기화
  useRouteInstanceSync();

  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar
        mobileOpen={mobileNavOpen}
        onCloseMobile={() => setMobileNavOpen(false)}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header
          onOpenMobileNav={() => setMobileNavOpen(true)}
          onCloseMobileNav={() => setMobileNavOpen(false)}
          mobileNavOpen={mobileNavOpen}
        />
        <main className="flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
