import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

/**
 * docs/spec2.md §6.1 — 정의되지 않은 라우트 접근 시 404 + 대시보드 복귀 링크.
 */
export default function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center">
      <p className="text-4xl font-semibold">404</p>
      <p className="text-muted-foreground">요청한 페이지를 찾을 수 없습니다.</p>
      <Button render={<Link to="/" />}>대시보드로 돌아가기</Button>
    </div>
  );
}
