import { useState } from "react";
import { NavLink, Link, useLocation, useParams } from "react-router-dom";
import {
  BarChart3,
  Briefcase,
  ChevronDown,
  ChevronRight,
  Cpu,
  Database,
  Eye,
  FileText,
  Folders,
  Gauge,
  History,
  LayoutDashboard,
  LineChart,
  Newspaper,
  Settings,
  SlidersHorizontal,
  Wifi,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";

/**
 * F4 라우트 재구성 후 nav 구조.
 *
 * 글로벌 메뉴:
 *  - 대시보드, 뉴스/공시, 차트 (read-only 뷰)
 *  - admin: 데이터수집 / 자산마스터 / LLM 모델 / 시스템파라미터 / audit-log / 전략 템플릿 / 브로커 계좌
 *
 * 전략별 메뉴:
 *  - `/strategy` (인스턴스 목록)
 *  - URL 이 `/strategy/{id}/...` 일 때만 sub-nav 노출:
 *    overview / paper-eval / trade-history / portfolio / trades / watchlist / prompt / settings
 */

const GLOBAL_NAV = [
  { to: "/", label: "대시보드", icon: LayoutDashboard, end: true },
  { to: "/news", label: "뉴스·공시", icon: Newspaper, end: false },
  { to: "/chart", label: "차트", icon: BarChart3, end: false },
] as const;

const ADMIN_CHILDREN = [
  { to: "/admin/data-collection", label: "데이터 수집", icon: Wifi },
  { to: "/admin/asset-master", label: "자산 마스터", icon: Database },
  { to: "/admin/llm-models", label: "LLM 모델", icon: Cpu },
  { to: "/admin/strategy-templates", label: "전략 템플릿", icon: Folders },
  { to: "/admin/broker-accounts", label: "브로커 계좌", icon: Briefcase },
  {
    to: "/admin/system-parameters",
    label: "시스템 파라미터",
    icon: SlidersHorizontal,
  },
  { to: "/admin/audit-log", label: "audit-log", icon: FileText },
] as const;

const INSTANCE_CHILDREN = (id: string) =>
  [
    {
      to: `/strategy/${id}/overview`,
      label: "개요",
      icon: LayoutDashboard,
    },
    {
      to: `/strategy/${id}/paper-eval`,
      label: "paper 평가",
      icon: Gauge,
    },
    {
      to: `/strategy/${id}/trade-history`,
      label: "매매 이력",
      icon: LineChart,
    },
    {
      to: `/strategy/${id}/portfolio`,
      label: "포트폴리오",
      icon: Briefcase,
    },
    {
      to: `/strategy/${id}/trades`,
      label: "매매·판단",
      icon: LineChart,
    },
    {
      to: `/strategy/${id}/watchlist`,
      label: "감시 종목",
      icon: Eye,
    },
    {
      to: `/strategy/${id}/prompt`,
      label: "프롬프트 버전",
      icon: History,
    },
    {
      to: `/strategy/${id}/settings`,
      label: "인스턴스 설정",
      icon: Settings,
    },
  ] as const;

interface SidebarProps {
  mobileOpen: boolean;
  onCloseMobile: () => void;
}

export default function Sidebar({ mobileOpen, onCloseMobile }: SidebarProps) {
  const location = useLocation();
  const routeParams = useParams();
  const instances = useStrategyInstances();

  const adminActive = location.pathname.startsWith("/admin");
  const strategyActive = location.pathname.startsWith("/strategy");
  // URL 에서 `/strategy/{id}` 인 경우 instance sub-nav 노출
  const instanceMatch = location.pathname.match(
    /^\/strategy\/([0-9a-fA-F-]{8,})/,
  );
  const instanceIdFromRoute = instanceMatch?.[1] ?? routeParams.id ?? null;
  const currentInstance =
    instanceIdFromRoute && instances.data
      ? instances.data.find((row) => row.id === instanceIdFromRoute) ?? null
      : null;

  const [adminToggle, setAdminToggle] = useState<boolean | null>(null);
  const [strategyToggle, setStrategyToggle] = useState<boolean | null>(null);
  const adminOpen = adminToggle ?? adminActive;
  const strategyOpen = strategyToggle ?? strategyActive;

  return (
    <>
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
          "fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r bg-sidebar text-sidebar-foreground transition-transform duration-200 md:sticky md:top-0 md:h-screen md:translate-x-0",
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

        <nav
          className="flex-1 overflow-y-auto p-3"
          aria-label="메인 메뉴"
        >
          {/* 글로벌 */}
          <ul className="flex flex-col gap-1">
            {GLOBAL_NAV.map(({ to, label, icon: Icon, end }) => (
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
          </ul>

          {/* 전략 */}
          <SidebarSectionHeader
            label="전략"
            active={strategyActive}
            open={strategyOpen}
            onToggle={() => setStrategyToggle(!strategyOpen)}
          />
          {strategyOpen ? (
            <ul className="mt-1 ml-3 flex flex-col gap-1 border-l border-sidebar-border pl-3">
              <li>
                <NavLink
                  to="/strategy"
                  end
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
                  <Folders className="size-4" />
                  인스턴스 목록
                </NavLink>
              </li>

              {instanceIdFromRoute ? (
                <>
                  <li className="mt-2 px-2.5 text-[11px] uppercase tracking-wide text-muted-foreground">
                    {currentInstance?.name ?? "인스턴스"}
                  </li>
                  {INSTANCE_CHILDREN(instanceIdFromRoute).map(
                    ({ to, label, icon: Icon }) => (
                      <li key={to}>
                        <NavLink
                          to={to}
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
                          <Icon className="size-4" />
                          {label}
                        </NavLink>
                      </li>
                    ),
                  )}
                </>
              ) : null}
            </ul>
          ) : null}

          {/* admin */}
          <SidebarSectionHeader
            label="admin"
            active={adminActive}
            open={adminOpen}
            onToggle={() => setAdminToggle(!adminOpen)}
          />
          {adminOpen ? (
            <ul className="mt-1 ml-3 flex flex-col gap-1 border-l border-sidebar-border pl-3">
              {ADMIN_CHILDREN.map(({ to, label, icon: Icon }) => (
                <li key={to}>
                  <NavLink
                    to={to}
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
                    <Icon className="size-4" />
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          ) : null}
        </nav>
      </aside>
    </>
  );
}

function SidebarSectionHeader({
  label,
  active,
  open,
  onToggle,
}: {
  label: string;
  active: boolean;
  open: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      aria-expanded={open}
      onClick={onToggle}
      className={cn(
        "mt-4 flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
        active
          ? "bg-sidebar-accent text-sidebar-accent-foreground"
          : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
      )}
    >
      <span className="flex-1 text-left text-[11px] uppercase tracking-wide">
        {label}
      </span>
      {open ? (
        <ChevronDown className="size-4" />
      ) : (
        <ChevronRight className="size-4" />
      )}
    </button>
  );
}
