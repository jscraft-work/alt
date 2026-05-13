package work.jscraft.alt.trading.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public final class PublicTradeViews {

    private PublicTradeViews() {
    }

    public record TradeOrderListItem(
            UUID id,
            UUID strategyInstanceId,
            String instanceName,
            String symbolCode,
            String symbolName,
            String side,
            String executionMode,
            String orderStatus,
            BigDecimal requestedQuantity,
            BigDecimal requestedPrice,
            BigDecimal filledQuantity,
            BigDecimal avgFilledPrice,
            OffsetDateTime requestedAt,
            OffsetDateTime filledAt) {
    }

    public record TradeOrderDetail(
            UUID id,
            UUID tradeOrderIntentId,
            UUID strategyInstanceId,
            String symbolCode,
            String symbolName,
            String clientOrderId,
            String brokerOrderNo,
            String executionMode,
            String orderStatus,
            BigDecimal requestedQuantity,
            BigDecimal requestedPrice,
            BigDecimal filledQuantity,
            BigDecimal avgFilledPrice,
            OffsetDateTime requestedAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime filledAt,
            String failureReason,
            JsonNode portfolioAfter) {
    }

    public record TradeDecisionListItem(
            UUID id,
            UUID strategyInstanceId,
            String instanceName,
            String cycleStatus,
            String summary,
            BigDecimal confidence,
            String failureReason,
            OffsetDateTime cycleStartedAt,
            OffsetDateTime cycleFinishedAt,
            int orderCount) {
    }

    public record TradeDecisionDetail(
            UUID id,
            UUID strategyInstanceId,
            String instanceName,
            String cycleStatus,
            String summary,
            BigDecimal confidence,
            String failureReason,
            String failureDetail,
            OffsetDateTime cycleStartedAt,
            OffsetDateTime cycleFinishedAt,
            String requestText,
            String responseText,
            String stdoutText,
            String stderrText,
            Integer inputTokens,
            Integer outputTokens,
            BigDecimal estimatedCost,
            String callStatus,
            String engineName,
            String modelName,
            JsonNode parsedDecision,
            JsonNode settingsSnapshot,
            List<OrderIntentView> orderIntents,
            List<OrderRefView> orders) {
    }

    public record OrderIntentView(
            UUID id,
            int sequenceNo,
            String symbolCode,
            String side,
            BigDecimal quantity,
            String orderType,
            BigDecimal price,
            String rationale,
            JsonNode evidence,
            String executionBlockedReason) {
    }

    public record OrderRefView(
            UUID id,
            UUID tradeOrderIntentId,
            String orderStatus,
            OffsetDateTime requestedAt) {
    }
}
