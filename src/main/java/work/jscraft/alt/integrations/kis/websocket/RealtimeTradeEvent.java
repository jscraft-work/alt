package work.jscraft.alt.integrations.kis.websocket;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RealtimeTradeEvent(
        String symbolCode,
        OffsetDateTime tradeTime,
        BigDecimal price,
        long cumulativeVolume,
        long buyQty,
        long sellQty) {
}
