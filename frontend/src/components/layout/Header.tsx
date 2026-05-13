import { Link, useNavigate } from "react-router-dom";
import { LogOut, Menu, Moon, Sun, User, X } from "lucide-react";
import { toast } from "sonner";
import { useTheme } from "@/hooks/use-theme";
import { useCurrentUser, useLogout } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import StrategyInstanceSelector from "./StrategyInstanceSelector";

/**
 * docs/spec2.md §6.1 — 우측 상단 테마 토글 + 로그인/로그아웃,
 * 모바일에서는 햄버거 메뉴 노출.
 *
 * 운영자 모드/공개 모드 구분: 인증 사용자가 있으면 사용자 아이콘과
 * 로그아웃 메뉴를 보여주고, 없으면 "로그인" 링크만 보여준다.
 */
interface HeaderProps {
  mobileNavOpen: boolean;
  onOpenMobileNav: () => void;
  onCloseMobileNav: () => void;
}

export default function Header({
  mobileNavOpen,
  onOpenMobileNav,
  onCloseMobileNav,
}: HeaderProps) {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const { data: currentUser } = useCurrentUser();
  const logout = useLogout();

  const handleLogout = () => {
    logout.mutate(undefined, {
      onSuccess: () => {
        toast.success("로그아웃되었습니다.");
        navigate("/login");
      },
      onError: (error) => {
        toast.error(error.message ?? "로그아웃에 실패했습니다.");
      },
    });
  };

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/60 md:px-8">
      {/* 좌측: 모바일 햄버거 */}
      <div className="flex min-w-0 items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="md:hidden"
          onClick={mobileNavOpen ? onCloseMobileNav : onOpenMobileNav}
          aria-label={mobileNavOpen ? "메뉴 닫기" : "메뉴 열기"}
          aria-expanded={mobileNavOpen}
        >
          {mobileNavOpen ? (
            <X className="size-5" />
          ) : (
            <Menu className="size-5" />
          )}
        </Button>

        <StrategyInstanceSelector />
      </div>

      {/* 우측: 테마 + 인증 */}
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleTheme}
          aria-label={
            theme === "dark" ? "라이트 모드로 전환" : "다크 모드로 전환"
          }
        >
          {theme === "dark" ? (
            <Moon className="size-5" />
          ) : (
            <Sun className="size-5" />
          )}
        </Button>

        {currentUser ? (
          <DropdownMenu>
            <DropdownMenuTrigger
              className="flex items-center justify-center rounded-full size-8 bg-muted hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring cursor-pointer"
              aria-label="사용자 메뉴"
            >
              <User className="size-4" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" sideOffset={8}>
              <div className="px-2 py-1.5 text-xs text-muted-foreground">
                {currentUser.displayName ?? currentUser.loginId}
              </div>
              <DropdownMenuItem onClick={handleLogout} disabled={logout.isPending}>
                <LogOut className="size-4" />
                로그아웃
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        ) : (
          <Link
            to="/login"
            className="text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            로그인
          </Link>
        )}
      </div>
    </header>
  );
}
