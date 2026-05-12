package work.jscraft.alt.dashboard.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DashboardViews {

    private DashboardViews() {
    }

    public record StrategyOverviewCardView(
            UUID strategyInstanceId,
            String name,
            String executionMode,
            String lifecycleState,
            String autoPausedReason,
            BigDecimal budgetAmount,
            BigDecimal cashAmount,
            BigDecimal totalAssetAmount,
            BigDecimal todayRealizedPnl,
            String latestDecisionStatus,
            OffsetDateTime latestDecisionAt,
            long watchlistCount) {
    }

    public record InstanceDashboardView(
            InstanceSummary instance,
            PortfolioSummary portfolio,
            List<PositionView> positions,
            List<SystemStatusEntryView> systemStatus,
            LatestDecisionView latestDecision,
            List<RecentOrderView> recentOrders) {
    }

    public record InstanceSummary(
            UUID id,
            String name,
            String executionMode,
            String lifecycleState,
            String autoPausedReason,
            BigDecimal budgetAmount,
            String brokerAccountMasked) {
    }

    public record PortfolioSummary(
            BigDecimal cashAmount,
            BigDecimal totalAssetAmount,
            BigDecimal realizedPnlToday) {
    }

    public record PositionView(
            String symbolCode,
            String symbolName,
            BigDecimal quantity,
            BigDecimal avgBuyPrice,
            BigDecimal lastMarkPrice,
            BigDecimal unrealizedPnl) {
    }

    public record SystemStatusEntryView(
            String serviceName,
            String statusCode,
            String message,
            OffsetDateTime occurredAt,
            OffsetDateTime lastSuccessAt) {
    }

    public record LatestDecisionView(
            UUID decisionLogId,
            String cycleStatus,
            String summary,
            OffsetDateTime cycleStartedAt) {
    }

    public record RecentOrderView(
            UUID tradeOrderId,
            String symbolCode,
            String side,
            String orderStatus,
            OffsetDateTime requestedAt,
            BigDecimal requestedQuantity,
            BigDecimal requestedPrice) {
    }
}
