import { AlertTriangle, CheckCircle2, CircleHelp, Loader2 } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useSystemStatus } from "@/hooks/use-dashboard";
import { formatKstDateTime } from "@/lib/format";
import type { SystemStatusCode, SystemStatusItem } from "@/lib/api-types";

/**
 * docs/spec2.md §7.1 시스템 상태 카드 + docs/04-api-spec.md §4.3.
 *
 * 임계값 자체(§7.1.1)는 서버에서 statusCode 로 내려준 값을 그대로 표시한다.
 * 프론트에서 KST/지연 계산을 다시 하지는 않는다.
 */
export default function SystemStatusSection() {
  const { data, isLoading, error } = useSystemStatus();

  return (
    <Card>
      <CardHeader>
        <CardTitle>시스템 상태</CardTitle>
        <CardDescription>
          외부 수집·트레이더·웹소켓 상태 (30초 갱신)
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> 불러오는 중...
          </div>
        ) : error ? (
          <div className="flex items-center gap-2 text-sm text-destructive">
            <AlertTriangle className="size-4" /> {error.message}
          </div>
        ) : !data || data.length === 0 ? (
          <p className="text-sm text-muted-foreground">표시할 상태 정보가 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {data.map((item) => (
              <SystemStatusRow key={item.serviceName} item={item} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

const SERVICE_LABEL: Record<string, string> = {
  marketdata: "시장 데이터",
  news: "뉴스 수집",
  dart: "DART 공시",
  websocket: "웹소켓",
  trader: "트레이더 사이클",
};

function SystemStatusRow({ item }: { item: SystemStatusItem }) {
  const label = SERVICE_LABEL[item.serviceName] ?? item.serviceName;
  return (
    <li className="flex items-center justify-between gap-3 rounded-md border bg-card px-3 py-2">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">{label}</p>
        <p className="truncate text-xs text-muted-foreground">
          마지막 성공: {formatKstDateTime(item.lastSuccessAt)}
        </p>
        {item.message && (
          <p className="truncate text-xs text-muted-foreground">{item.message}</p>
        )}
      </div>
      <StatusBadge code={item.statusCode} />
    </li>
  );
}

function StatusBadge({ code }: { code: SystemStatusCode }) {
  switch (code) {
    case "ok":
      return (
        <Badge variant="secondary" className="gap-1">
          <CheckCircle2 className="size-3" /> 정상
        </Badge>
      );
    case "delayed":
      return (
        <Badge variant="outline" className="gap-1 text-warning">
          <AlertTriangle className="size-3" /> 지연
        </Badge>
      );
    case "down":
      return (
        <Badge variant="destructive" className="gap-1">
          <AlertTriangle className="size-3" /> 중단
        </Badge>
      );
    default:
      return (
        <Badge variant="outline" className="gap-1">
          <CircleHelp className="size-3" /> 알 수 없음
        </Badge>
      );
  }
}
