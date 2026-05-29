import { Link, useParams } from "react-router-dom";
import {
  AlertTriangle,
  Briefcase,
  Eye,
  Gauge,
  History,
  LineChart,
  Loader2,
  Pencil,
  Settings,
  Timer,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import SettingsHeader from "@/components/settings/SettingsHeader";
import { useStrategyInstances } from "@/hooks/use-strategy-instances";
import { useInstanceDashboard } from "@/hooks/use-dashboard";
import { formatKrw, formatKstDateTime } from "@/lib/format";

/**
 * 전략 인스턴스 요약 페이지 (F4 신규).
 *
 * - lifecycle / executionMode / budget / cycle_minutes / 연결계좌
 * - latestDecision (최근 cycle status + 시간) + 보유 포지션 수 + 감시 종목 수
 * - 사이드 카드: 관련 페이지 빠른 이동
 */
export default function StrategyOverviewPage() {
  const params = useParams();
  const instanceId = params.id ?? "";
  const instances = useStrategyInstances();
  const dashboard = useInstanceDashboard(instanceId || null);
  const instance = instances.data?.find((row) => row.id === instanceId);

  if (instances.isLoading && !instance) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" /> 인스턴스 정보 불러오는 중…
      </div>
    );
  }

  if (instances.error) {
    return <p className="text-sm text-destructive">{instances.error.message}</p>;
  }

  if (!instance) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>인스턴스를 찾을 수 없습니다</CardTitle>
          <CardDescription>
            URL 의 인스턴스 ID 가 존재하지 않거나 삭제됐을 수 있습니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button render={<Link to="/strategy" />}>인스턴스 목록</Button>
        </CardContent>
      </Card>
    );
  }

  const latest = dashboard.data?.latestDecision ?? null;

  return (
    <div className="flex flex-col gap-6">
      <SettingsHeader
        title={`${instance.name} · 개요`}
        description={`${instance.strategyTemplateName} 템플릿 기반 인스턴스. lifecycle, 자산, 최근 cycle status 를 한 화면에서 확인.`}
        action={
          <Button
            variant="outline"
            render={<Link to={`/strategy/${instanceId}/settings`} />}
          >
            <Pencil className="size-4" /> 인스턴스 편집
          </Button>
        }
      />

      <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          icon={<Timer className="size-4" />}
          label="lifecycle"
          value={
            <span className="flex flex-wrap items-center gap-2">
              <LifecycleBadge state={instance.lifecycleState} />
              {instance.autoPausedReason ? (
                <Badge variant="destructive">AUTO-PAUSED</Badge>
              ) : null}
            </span>
          }
          hint={
            instance.autoPausedAt
              ? `auto-paused ${formatKstDateTime(instance.autoPausedAt)}`
              : `updated ${formatKstDateTime(instance.updatedAt)}`
          }
        />
        <SummaryCard
          icon={<Briefcase className="size-4" />}
          label="실행 모드 / 예산"
          value={
            <span className="flex items-baseline gap-2">
              <Badge variant="secondary">
                {instance.executionMode.toUpperCase()}
              </Badge>
              <span className="text-base tabular-nums">
                {formatKrw(instance.budgetAmount)}
              </span>
            </span>
          }
          hint={
            instance.brokerAccountMasked
              ? `계좌 ${instance.brokerAccountMasked}`
              : "연결 계좌 없음"
          }
        />
        <SummaryCard
          icon={<Timer className="size-4" />}
          label="cycle 주기"
          value={
            instance.cycleMinutes == null
              ? `${instance.effectiveCycleMinutes}분 (템플릿)`
              : `${instance.cycleMinutes}분`
          }
          hint="cycle_minutes (override) / effective"
        />
        <SummaryCard
          icon={<Gauge className="size-4" />}
          label="최근 cycle"
          value={
            dashboard.isLoading ? (
              <Loader2 className="inline size-4 animate-spin text-muted-foreground" />
            ) : latest ? (
              <CycleStatusBadge code={latest.cycleStatus} />
            ) : (
              <span className="text-muted-foreground">기록 없음</span>
            )
          }
          hint={latest ? formatKstDateTime(latest.cycleStartedAt) : undefined}
        />
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_320px]">
        {/* 메인: 최근 cycle 상세 + 자산 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">최근 cycle</CardTitle>
            <CardDescription>가장 최근 trade_decision_log row.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {dashboard.isLoading ? (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" /> 불러오는 중…
              </p>
            ) : dashboard.error ? (
              <p className="flex items-center gap-2 text-sm text-destructive">
                <AlertTriangle className="size-4" /> {dashboard.error.message}
              </p>
            ) : latest ? (
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <CycleStatusBadge code={latest.cycleStatus} />
                  <span className="text-xs text-muted-foreground">
                    {formatKstDateTime(latest.cycleStartedAt)}
                  </span>
                </div>
                <p className="rounded-md border bg-muted/40 p-3 text-sm">
                  {latest.summary ?? "판단 요약이 없습니다."}
                </p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                최근 cycle 기록이 없습니다. (lifecycle 이 active 상태인지 확인)
              </p>
            )}

            {dashboard.data ? (
              <dl className="grid grid-cols-2 gap-x-4 gap-y-2 pt-3 text-sm sm:grid-cols-3">
                <div>
                  <dt className="text-xs text-muted-foreground">총 자산</dt>
                  <dd className="font-medium tabular-nums">
                    {formatKrw(dashboard.data.portfolio.totalAssetAmount)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">현금</dt>
                  <dd className="font-medium tabular-nums">
                    {formatKrw(dashboard.data.portfolio.cashAmount)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">오늘 실현손익</dt>
                  <dd className="font-medium tabular-nums">
                    {formatKrw(dashboard.data.portfolio.realizedPnlToday)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">보유 포지션</dt>
                  <dd className="font-medium tabular-nums">
                    {dashboard.data.positions.length}종목
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">최근 주문</dt>
                  <dd className="font-medium tabular-nums">
                    {dashboard.data.recentOrders.length}건
                  </dd>
                </div>
              </dl>
            ) : null}
          </CardContent>
        </Card>

        {/* 사이드: 관련 페이지 빠른 진입 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">관련 페이지</CardTitle>
            <CardDescription>이 인스턴스의 상세 화면 진입.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            <SideLink
              to={`/paper-eval?instanceId=${instanceId}`}
              icon={<Gauge className="size-4" />}
              label="paper 평가"
              hint="4지표 + 시계열 + 직전 N건"
            />
            <SideLink
              to={`/trade-history?instanceId=${instanceId}`}
              icon={<LineChart className="size-4" />}
              label="매매 이력"
              hint="페이지네이션 + 필터"
            />
            <SideLink
              to={`/portfolio?instanceId=${instanceId}`}
              icon={<Briefcase className="size-4" />}
              label="포트폴리오"
              hint="보유 종목 + 최근 활동"
            />
            <SideLink
              to={`/strategy/${instanceId}/watchlist`}
              icon={<Eye className="size-4" />}
              label="감시 종목"
              hint="watchlist CRUD"
            />
            <SideLink
              to={`/strategy/${instanceId}/prompt`}
              icon={<History className="size-4" />}
              label="프롬프트 버전"
              hint="버전 이력 + 복원"
            />
            <SideLink
              to={`/strategy/${instanceId}/settings`}
              icon={<Settings className="size-4" />}
              label="인스턴스 설정"
              hint="lifecycle / 예산 / cycle"
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function SummaryCard({
  icon,
  label,
  value,
  hint,
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  hint?: string;
}) {
  return (
    <Card>
      <CardHeader className="pb-1">
        <CardTitle className="flex items-center gap-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {icon}
          {label}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-1">
        <span className="text-base font-semibold">{value}</span>
        {hint ? (
          <span className="text-xs text-muted-foreground">{hint}</span>
        ) : null}
      </CardContent>
    </Card>
  );
}

function SideLink({
  to,
  icon,
  label,
  hint,
}: {
  to: string;
  icon: React.ReactNode;
  label: string;
  hint?: string;
}) {
  return (
    <Link
      to={to}
      className="flex items-start gap-3 rounded-md border p-3 transition-colors hover:bg-muted/50"
    >
      <span className="mt-0.5 text-muted-foreground">{icon}</span>
      <span className="flex min-w-0 flex-col">
        <span className="text-sm font-medium">{label}</span>
        {hint ? (
          <span className="truncate text-xs text-muted-foreground">{hint}</span>
        ) : null}
      </span>
    </Link>
  );
}

function LifecycleBadge({ state }: { state: string }) {
  const variant: "default" | "secondary" | "outline" =
    state === "active"
      ? "default"
      : state === "draft"
        ? "outline"
        : "secondary";
  return <Badge variant={variant}>{state}</Badge>;
}

function CycleStatusBadge({ code }: { code: string }) {
  if (code === "EXECUTE") {
    return <Badge className="bg-profit text-white">EXECUTE</Badge>;
  }
  if (code === "HOLD") {
    return <Badge variant="secondary">HOLD</Badge>;
  }
  return <Badge variant="destructive">{code}</Badge>;
}
