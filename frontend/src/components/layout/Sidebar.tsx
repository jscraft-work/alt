import { useState } from "react";
import { NavLink, Link, useLocation } from "react-router-dom";
import {
  BarChart3,
  Briefcase,
  ChevronDown,
  ChevronRight,
  Cpu,
  Database,
  Eye,
  Folders,
  History,
  LayoutDashboard,
  LineChart,
  Newspaper,
  Settings,
  SlidersHorizontal,
  ServerCog,
} from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * docs/spec2.md §6.1 / §6.2 — 좌측 상단 로고, 네비게이션 항목.
 *
 * `/settings` 는 단일 항목이 아니라 여러 sub-route 로 확장된다 (Phase 1.5).
 */
const NAV_ITEMS = [
  { to: "/", label: "대시보드", icon: LayoutDashboard, end: true },
  { to: "/trades", label: "매매이력", icon: LineChart, end: false },
  { to: "/news", label: "뉴스·공시", icon: Newspaper, end: false },
  { to: "/chart", label: "차트", icon: BarChart3, end: false },
] as const;

const SETTINGS_CHILDREN = [
  { to: "/settings/instances", label: "전략 인스턴스", icon: ServerCog },
  { to: "/settings/templates", label: "전략 템플릿", icon: Folders },
  { to: "/settings/assets", label: "자산 마스터", icon: Database },
  { to: "/settings/models", label: "모델 프로필", icon: Cpu },
  { to: "/settings/accounts", label: "브로커 계좌", icon: Briefcase },
  {
    to: "/settings/system-parameters",
    label: "시스템 파라미터",
    icon: SlidersHorizontal,
  },
] as const;

interface SidebarProps {
  mobileOpen: boolean;
  onCloseMobile: () => void;
}

export default function Sidebar({ mobileOpen, onCloseMobile }: SidebarProps) {
  const location = useLocation();
  const settingsActive = location.pathname.startsWith("/settings");
  // 사용자가 명시적으로 toggle 했는지 추적한다. null 이면 active 경로에 따라 자동.
  const [userToggle, setUserToggle] = useState<boolean | null>(null);
  const settingsOpen = userToggle ?? settingsActive;

  // 특정 인스턴스 하위 settings 페이지에서는 sibling link 를 함께 노출한다.
  const instanceSettingsMatch = location.pathname.match(
    /^\/settings\/instances\/([^/]+)\/(watchlist|prompt-versions)/,
  );
  const instanceSettingsBase = instanceSettingsMatch
    ? `/settings/instances/${instanceSettingsMatch[1]}`
    : null;

  return (
    <>
      {/* 모바일 backdrop */}
      {mobileOpen && (
        <button
          type="button"
          className="fixed inset-0 z-30 bg-background/80 backdrop-blur-sm md:hidden"
          aria-label="메뉴 닫기"
          onClick={onCloseMobile}
        />
      )}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r bg-sidebar text-sidebar-foreground transition-transform duration-200 md:sticky md:top-0 md:h-screen md:translate-x-0",
          mobileOpen ? "translate-x-0" : "-translate-x-full",
        )}
        aria-label="사이드 네비게이션"
      >
        <div className="flex h-14 items-center border-b px-4">
          <Link
            to="/"
            className="text-lg font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
            onClick={onCloseMobile}
            aria-label="ALT 홈으로 이동"
          >
            ALT
          </Link>
        </div>
        <nav className="flex-1 overflow-y-auto p-3" aria-label="메인 메뉴">
          <ul className="flex flex-col gap-1">
            {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  onClick={onCloseMobile}
                  className={({ isActive }) =>
                    cn(
                      "flex items-center gap-2 rounded-md px-2.5 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
                      isActive
                        ? "bg-sidebar-accent text-sidebar-accent-foreground"
                        : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
                    )
                  }
                >
                  <Icon className="size-4" />
                  {label}
                </NavLink>
              </li>
            ))}

            <li>
              <button
                type="button"
                aria-expanded={settingsOpen}
                aria-controls="sidebar-settings-children"
                onClick={() => setUserToggle(!settingsOpen)}
                className={cn(
                  "flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
                  settingsActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
                )}
              >
                <Settings className="size-4" />
                <span className="flex-1 text-left">설정</span>
                {settingsOpen ? (
                  <ChevronDown className="size-4" />
                ) : (
                  <ChevronRight className="size-4" />
                )}
              </button>
              {settingsOpen && (
                <ul
                  id="sidebar-settings-children"
                  className="mt-1 ml-3 flex flex-col gap-1 border-l border-sidebar-border pl-3"
                >
                  {SETTINGS_CHILDREN.map(({ to, label, icon: Icon }) => (
                    <li key={to}>
                      <NavLink
                        to={to}
                        onClick={onCloseMobile}
                        className={({ isActive }) =>
                          cn(
                            "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
                            isActive
                              ? "bg-sidebar-accent text-sidebar-accent-foreground"
                              : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
                          )
                        }
                      >
                        <Icon className="size-4" />
                        {label}
                      </NavLink>
                    </li>
                  ))}
                  {instanceSettingsBase ? (
                    <>
                      <li>
                        <NavLink
                          to={`${instanceSettingsBase}/watchlist`}
                          onClick={onCloseMobile}
                          className={({ isActive }) =>
                            cn(
                              "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm transition-colors",
                              isActive
                                ? "bg-sidebar-accent text-sidebar-accent-foreground"
                                : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
                            )
                          }
                        >
                          <Eye className="size-4" />
                          감시 종목
                        </NavLink>
                      </li>
                      <li>
                        <NavLink
                          to={`${instanceSettingsBase}/prompt-versions`}
                          onClick={onCloseMobile}
                          className={({ isActive }) =>
                            cn(
                              "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm transition-colors",
                              isActive
                                ? "bg-sidebar-accent text-sidebar-accent-foreground"
                                : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
                            )
                          }
                        >
                          <History className="size-4" />
                          프롬프트 버전
                        </NavLink>
                      </li>
                    </>
                  ) : null}
                </ul>
              )}
            </li>
          </ul>
        </nav>
      </aside>
    </>
  );
}
