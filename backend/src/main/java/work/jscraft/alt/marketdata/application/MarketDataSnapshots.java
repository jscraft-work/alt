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

    /**
     * KIS inquire-price 응답의 펀더멘털 부분 (PER/PBR/EPS/BPS, 52주·250일 고저, 외국인/신용/종목 상태 등).
     * Adapter는 KIS output JsonNode를 그대로 전달하고, Collector가 키 단위로 Entity에 매핑한다.
     */
    public record FundamentalSnapshot(
            String symbolCode,
            OffsetDateTime snapshotAt,
            JsonNode rawOutput,
            String sourceName) {
    }
}
