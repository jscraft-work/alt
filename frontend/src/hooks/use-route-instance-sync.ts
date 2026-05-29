import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";

/**
 * 라우트 URL ↔ StrategyInstanceSelectionProvider 동기화 (F4).
 *
 * - `/strategy/{id}/*` path 에 진입 시 Provider 의 selectedInstanceId 를 URL 의 :id 로 자동 설정
 *   → DashboardPage, AppShell 의 인스턴스 선택 UI 등 전역 컨텍스트와 정합
 * - `/admin/*` 또는 다른 글로벌 path 는 변경하지 않음 (사용자가 명시적으로 선택한 인스턴스 유지)
 */
const STRATEGY_PATH_PATTERN = /^\/strategy\/([0-9a-fA-F-]{8,})/;

export function useRouteInstanceSync() {
  const location = useLocation();
  const { selectedInstanceId, setSelectedInstanceId } =
    useStrategyInstanceSelection();

  useEffect(() => {
    const match = location.pathname.match(STRATEGY_PATH_PATTERN);
    if (!match) {
      return;
    }
    const idFromRoute = match[1];
    if (idFromRoute && idFromRoute !== selectedInstanceId) {
      setSelectedInstanceId(idFromRoute);
    }
  }, [location.pathname, selectedInstanceId, setSelectedInstanceId]);
}
