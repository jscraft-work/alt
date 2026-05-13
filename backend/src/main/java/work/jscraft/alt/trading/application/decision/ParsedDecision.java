package work.jscraft.alt.trading.application.decision;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record ParsedDecision(
        String cycleStatus,
        String summary,
        BigDecimal confidence,
        BoxEstimate boxEstimate,
        List<ParsedOrder> orders) {

    public record ParsedOrder(
            int sequenceNo,
            String symbolCode,
            String side,
            BigDecimal quantity,
            String orderType,
            BigDecimal price,
            String rationale,
            JsonNode evidence) {
    }

    public record BoxEstimate(
            BigDecimal low,
            BigDecimal high,
            BigDecimal confidence) {
    }
}
