import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useCurrentUser, useLogin } from "@/lib/auth";

/**
 * 로그인 페이지 — docs/spec2.md §7.7 + docs/04-api-spec.md §3.1.
 *
 * - 성공 시 `?next=<경로>` 가 있으면 그 경로로, 없으면 대시보드로 이동.
 * - 실패 시 envelope 의 message 를 그대로 노출.
 *   - TOO_MANY_REQUESTS / 429: 차단 메시지 처리 (서버 메시지 그대로 노출)
 *   - VALIDATION_ERROR: fieldErrors 가 있으면 함께 노출.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const next = searchParams.get("next");

  const { data: currentUser } = useCurrentUser();
  const login = useLogin();

  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");

  // 이미 로그인되어 있으면 next 또는 대시보드로 보낸다.
  useEffect(() => {
    if (currentUser) {
      navigate(next ?? "/", { replace: true });
    }
  }, [currentUser, navigate, next]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!loginId.trim() || !password) return;
    login.mutate(
      { loginId: loginId.trim(), password },
      {
        onSuccess: () => {
          navigate(next ?? "/", { replace: true });
        },
      },
    );
  };

  const error = login.error;
  const fieldErrors = error?.fieldErrors ?? [];

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>로그인</CardTitle>
          <CardDescription>운영자 계정으로 로그인합니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="loginId">아이디</Label>
              <Input
                id="loginId"
                name="loginId"
                autoComplete="username"
                autoFocus
                required
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                disabled={login.isPending}
              />
              {fieldErrors
                .filter((f) => f.field === "loginId")
                .map((f) => (
                  <p key={f.field + f.message} className="text-xs text-destructive">
                    {f.message}
                  </p>
                ))}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={login.isPending}
              />
              {fieldErrors
                .filter((f) => f.field === "password")
                .map((f) => (
                  <p key={f.field + f.message} className="text-xs text-destructive">
                    {f.message}
                  </p>
                ))}
            </div>

            {error && (
              <div
                role="alert"
                className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {error.message}
              </div>
            )}

            <Button type="submit" disabled={login.isPending}>
              {login.isPending && <Loader2 className="size-4 animate-spin" />}
              로그인
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
