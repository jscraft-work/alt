package work.jscraft.alt.marketdata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ChartViews {

    private ChartViews() {
    }

    public record MinuteBarsResponse(
            String symbolCode,
            LocalDate date,
            List<MinuteBar> bars) {
    }

    public record MinuteBar(
            OffsetDateTime barTime,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal volume) {
    }

    public record OrderOverlay(
            UUID tradeOrderId,
            String side,
            String orderStatus,
            OffsetDateTime requestedAt,
            OffsetDateTime filledAt,
            BigDecimal requestedPrice,
            BigDecimal avgFilledPrice,
            BigDecimal requestedQuantity) {
    }
}
