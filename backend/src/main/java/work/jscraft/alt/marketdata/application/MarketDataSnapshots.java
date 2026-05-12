package work.jscraft.alt.marketdata.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

public final class MarketDataSnapshots {

    private MarketDataSnapshots() {
    }

    public record PriceSnapshot(
            String symbolCode,
            OffsetDateTime snapshotAt,
            BigDecimal lastPrice,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal volume,
            String sourceName) {
    }

    public record MinuteBar(
            String symbolCode,
            OffsetDateTime barTime,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal volume,
            String sourceName) {
    }

    public record OrderBookSnapshot(
            String symbolCode,
            OffsetDateTime capturedAt,
            JsonNode payload,
            String sourceName) {
    }
}
