import { Navigate, useLocation } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { useCurrentUser } from "@/lib/auth";

/**
 * 운영자 전용 라우트 가드.
 *
 * docs/spec2.md §4.2, §6.2 — 미인증 시 `/login?next=<원래 경로>`로 redirect.
 *
 * `useCurrentUser` 는 401 에서 `data === null` 을 돌려준다. (lib/auth.ts 참고)
 */
interface RequireAuthProps {
  children: React.ReactNode;
}

export default function RequireAuth({ children }: RequireAuthProps) {
  const location = useLocation();
  const { data: user, isLoading } = useCurrentUser();

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center py-16 text-muted-foreground">
        <Loader2 className="size-5 animate-spin" />
      </div>
    );
  }

  if (!user) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  return <>{children}</>;
}
