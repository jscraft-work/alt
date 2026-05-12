package work.jscraft.alt.integrations.kis.websocket;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;

/**
 * KIS realtime WebSocket frame decoder.
 *
 * <p>Pure functions only: this class has no Spring lifecycle. Construct it manually.</p>
 */
public class KisWebSocketFrameDecoder {

    public static final String TR_TRADE = "H0STCNT0";
    public static final String TR_ORDERBOOK = "H0STASP0";
    public static final String TR_PINGPONG = "PINGPONG";
    public static final String SOURCE_NAME = "kis-ws";

    private static final Logger log = LoggerFactory.getLogger(KisWebSocketFrameDecoder.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int TRADE_FIELDS_PER_ITEM = 46;
    private static final int ORDERBOOK_FIELDS_PER_ITEM = 59;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public KisWebSocketFrameDecoder(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public DecodedFrame decode(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new Unknown("empty");
        }
        String trimmed = rawText.trim();
        if (trimmed.isEmpty()) {
            return new Unknown("blank");
        }

        char head = trimmed.charAt(0);
        if (head == '{') {
            return decodeControl(trimmed);
        }
        if (head == '0' || head == '1') {
            return decodePipeFrame(trimmed, head == '1');
        }
        return new Unknown("unrecognized prefix");
    }

    private DecodedFrame decodeControl(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            return new ControlMessage(root);
        } catch (JsonProcessingException ex) {
            return new Unknown("malformed JSON control: " + ex.getOriginalMessage());
        }
    }

    private DecodedFrame decodePipeFrame(String raw, boolean encrypted) {
        // Format: <encrypted>|<TR_ID>|<count>|<DATA>
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) {
            return new Unknown("truncated pipe frame");
        }
        if (encrypted) {
            return new EncryptedFrame();
        }
        String trId = parts[1];
        int count;
        try {
            count = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ex) {
            return new Unknown("invalid count: " + parts[2]);
        }
        if (count <= 0) {
            return new Unknown("non-positive count");
        }
        String data = parts[3];
        String[] fields = data.split("\\^", -1);

        return switch (trId) {
            case TR_TRADE -> decodeTradeFrames(fields, count);
            case TR_ORDERBOOK -> decodeOrderBookFrames(fields, count);
            default -> new Unknown("unsupported tr_id: " + trId);
        };
    }

    private DecodedFrame decodeTradeFrames(String[] fields, int count) {
        int needed = count * TRADE_FIELDS_PER_ITEM;
        if (fields.length < needed) {
            return new Unknown("trade frame size mismatch: have=" + fields.length
                    + " need=" + needed);
        }
        List<TradeTick> ticks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int base = i * TRADE_FIELDS_PER_ITEM;
            String symbolCode = fields[base];
            String hhmmss = fields[base + 1];
            BigDecimal price = parseDecimal(fields[base + 2]);
            long cumulativeVolume = parseLong(fields[base + 13]);
            long sellQty = parseLong(fields[base + 19]);
            long buyQty = parseLong(fields[base + 20]);
            OffsetDateTime tradeTime = parseKstTime(hhmmss);
            ticks.add(new TradeTick(symbolCode, tradeTime, price,
                    cumulativeVolume, buyQty, sellQty));
        }
        return new TradeFrames(List.copyOf(ticks));
    }

    private DecodedFrame decodeOrderBookFrames(String[] fields, int count) {
        int needed = count * ORDERBOOK_FIELDS_PER_ITEM;
        if (fields.length < needed) {
            return new Unknown("orderbook frame size mismatch: have=" + fields.length
                    + " need=" + needed);
        }
        List<OrderBookLevels> books = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int base = i * ORDERBOOK_FIELDS_PER_ITEM;
            String symbolCode = fields[base];
            String hhmmss = fields[base + 1];
            OffsetDateTime capturedAt = parseKstTime(hhmmss);

            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode askPrices = payload.putArray("ask_prices");
            ArrayNode bidPrices = payload.putArray("bid_prices");
            ArrayNode askVolumes = payload.putArray("ask_volumes");
            ArrayNode bidVolumes = payload.putArray("bid_volumes");

            for (int j = 0; j < 10; j++) {
                askPrices.add(parseLong(fields[base + 3 + j]));
                bidPrices.add(parseLong(fields[base + 13 + j]));
                askVolumes.add(parseLong(fields[base + 23 + j]));
                bidVolumes.add(parseLong(fields[base + 33 + j]));
            }
            payload.put("total_ask_volume", parseLong(fields[base + 43]));
            payload.put("total_bid_volume", parseLong(fields[base + 44]));

            OrderBookSnapshot snapshot = new OrderBookSnapshot(
                    symbolCode, capturedAt, payload, SOURCE_NAME);
            books.add(new OrderBookLevels(snapshot));
        }
        return new OrderBookFrames(List.copyOf(books));
    }

    private OffsetDateTime parseKstTime(String hhmmss) {
        try {
            if (hhmmss == null || hhmmss.length() < 6) {
                return fallbackNow();
            }
            int hh = Integer.parseInt(hhmmss.substring(0, 2));
            int mm = Integer.parseInt(hhmmss.substring(2, 4));
            int ss = Integer.parseInt(hhmmss.substring(4, 6));
            LocalDate today = LocalDate.now(clock.withZone(KST));
            LocalDateTime ldt = LocalDateTime.of(today, LocalTime.of(hh, mm, ss));
            ZonedDateTime zdt = ldt.atZone(KST);
            return zdt.toOffsetDateTime();
        } catch (RuntimeException ex) {
            log.debug("KIS WS bad HHmmss: len={}", hhmmss == null ? -1 : hhmmss.length());
            return fallbackNow();
        }
    }

    private OffsetDateTime fallbackNow() {
        return OffsetDateTime.now(clock.withZone(KST));
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public sealed interface DecodedFrame
            permits ControlMessage, TradeFrames, OrderBookFrames, EncryptedFrame, Unknown {
    }

    public record ControlMessage(JsonNode body) implements DecodedFrame {
    }

    public record TradeFrames(List<TradeTick> ticks) implements DecodedFrame {
    }

    public record OrderBookFrames(List<OrderBookLevels> books) implements DecodedFrame {
    }

    public record EncryptedFrame() implements DecodedFrame {
    }

    public record Unknown(String reason) implements DecodedFrame {
    }

    public record TradeTick(
            String symbolCode,
            OffsetDateTime tradeTime,
            BigDecimal price,
            long cumulativeVolume,
            long buyQty,
            long sellQty) {
    }

    public record OrderBookLevels(OrderBookSnapshot snapshot) {
    }
}
