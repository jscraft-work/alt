import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { useStrategyInstanceSelection } from "@/hooks/use-strategy-instance-selection";

/**
 * URL query param `?instanceId=...` ↔ StrategyInstanceSelectionProvider 양방향 동기화 (F6).
 *
 * 모델:
 *  - "글로벌" 페이지 (`/paper-eval`, `/trade-history`, `/portfolio`, `/trades`, `/chart`) 중
 *    인스턴스 단위 데이터를 보여줘야 하는 페이지가 이 hook 을 호출한다.
 *  - URL 의 `instanceId` 는 **공유 가능한 진실값**.
 *  - Provider 의 selectedInstanceId 는 **localStorage 기반 세션 기억값**.
 *
 * 동작:
 *  - 첫 마운트:
 *    · URL 에 `instanceId` 있으면 → Provider 에 set (URL 이 이긴다, 공유 링크 우선)
 *    · URL 에 없고 Provider 에 있으면 → URL 에 promote (북마크 가능)
 *  - 이후 Provider 변경: → URL 에 반영 (selector 클릭 → 주소 즉시 업데이트)
 *  - 이후 URL 변경 (브라우저 뒤로/앞으로): → Provider 에 set
 *
 * 마운트 race 방지:
 *  - `hydratedRef` 로 hydrate effect 단 한 번만 실행.
 *  - `providerSyncRanRef` 로 Provider→URL effect 의 첫 firing 을 skip (hydrate 가 막 schedule 한
 *    setSelectedInstanceId 가 적용되기 전 stale closure 로 URL 을 잘못 지우는 race 차단).
 */
export function usePageInstanceSync(): void {
  const [searchParams, setSearchParams] = useSearchParams();
  const { selectedInstanceId, setSelectedInstanceId } =
    useStrategyInstanceSelection();
  const urlInstanceId = searchParams.get("instanceId");
  const hydratedRef = useRef(false);
  const providerSyncRanRef = useRef(false);

  // 첫 마운트 — URL 우선, 없으면 Provider 를 URL 로 promote
  useEffect(() => {
    if (hydratedRef.current) return;
    hydratedRef.current = true;

    if (urlInstanceId) {
      if (urlInstanceId !== selectedInstanceId) {
        setSelectedInstanceId(urlInstanceId);
      }
      return;
    }

    if (selectedInstanceId) {
      const next = new URLSearchParams(searchParams);
      next.set("instanceId", selectedInstanceId);
      setSearchParams(next, { replace: true });
    }
    // 의도적 [] — 마운트 한 번만 실행. 이후 동기화는 아래 두 effect 가 담당.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Provider → URL (selector 변경)
  useEffect(() => {
    if (!hydratedRef.current) return;
    if (!providerSyncRanRef.current) {
      // 첫 firing 은 hydrate 와 동일 commit phase 에 발생 — closure 의 selectedInstanceId 가
      // hydrate 가 막 schedule 한 새 값을 못 보므로 잘못된 URL update 가 일어날 수 있다. skip.
      providerSyncRanRef.current = true;
      return;
    }
    const currentUrlId = searchParams.get("instanceId");
    if ((selectedInstanceId ?? null) === (currentUrlId ?? null)) return;
    const next = new URLSearchParams(searchParams);
    if (selectedInstanceId) {
      next.set("instanceId", selectedInstanceId);
    } else {
      next.delete("instanceId");
    }
    setSearchParams(next, { replace: true });
    // urlInstanceId / searchParams 변경에 반응하면 ping-pong 위험. selector 클릭 시 trigger 되는 selectedInstanceId 만 deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedInstanceId]);

  // URL → Provider (브라우저 back/forward 또는 외부 링크)
  useEffect(() => {
    if (!hydratedRef.current) return;
    if (urlInstanceId && urlInstanceId !== selectedInstanceId) {
      setSelectedInstanceId(urlInstanceId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlInstanceId]);
}
