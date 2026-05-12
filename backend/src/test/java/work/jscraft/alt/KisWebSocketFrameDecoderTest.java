package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.ControlMessage;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.DecodedFrame;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.EncryptedFrame;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.OrderBookFrames;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.TradeFrames;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.TradeTick;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketFrameDecoder.Unknown;

import static org.assertj.core.api.Assertions.assertThat;

class KisWebSocketFrameDecoderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-11T02:00:00Z"), KST); // 2026-05-11 11:00 KST

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KisWebSocketFrameDecoder decoder = new KisWebSocketFrameDecoder(objectMapper, FIXED_CLOCK);

    @Test
    void jsonControlMessageIsParsed() {
        String json = "{\"header\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\"005930\"},"
                + "\"body\":{\"rt_cd\":\"0\",\"msg_cd\":\"OPSP0000\",\"msg1\":\"SUBSCRIBE SUCCESS\"}}";
        DecodedFrame frame = decoder.decode(json);

        assertThat(frame).isInstanceOf(ControlMessage.class);
        JsonNode body = ((ControlMessage) frame).body();
        assertThat(body.path("header").path("tr_id").asText()).isEqualTo("H0STCNT0");
        assertThat(body.path("header").path("tr_key").asText()).isEqualTo("005930");
        assertThat(body.path("body").path("msg1").asText()).isEqualTo("SUBSCRIBE SUCCESS");
    }

    @Test
    void pingpongControlMessageIsParsed() {
        String json = "{\"header\":{\"tr_id\":\"PINGPONG\"}}";
        DecodedFrame frame = decoder.decode(json);
        assertThat(frame).isInstanceOf(ControlMessage.class);
        assertThat(((ControlMessage) frame).body().path("header").path("tr_id").asText())
                .isEqualTo("PINGPONG");
    }

    @Test
    void tradeFrameSingleItem() {
        String[] f = new String[46];
        java.util.Arrays.fill(f, "0");
        f[0] = "005930";
        f[1] = "093015";
        f[2] = "81000";
        f[12] = "5";        // CNTG_VOL
        f[13] = "1500";     // ACML_VOL
        f[19] = "700";      // SELN
        f[20] = "800";      // SHNU
        String raw = "0|H0STCNT0|001|" + String.join("^", f);

        DecodedFrame frame = decoder.decode(raw);
        assertThat(frame).isInstanceOf(TradeFrames.class);
        TradeFrames tf = (TradeFrames) frame;
        assertThat(tf.ticks()).hasSize(1);

        TradeTick tick = tf.ticks().get(0);
        assertThat(tick.symbolCode()).isEqualTo("005930");
        assertThat(tick.price()).isEqualByComparingTo(new BigDecimal("81000"));
        assertThat(tick.cumulativeVolume()).isEqualTo(1500L);
        assertThat(tick.sellQty()).isEqualTo(700L);
        assertThat(tick.buyQty()).isEqualTo(800L);
        assertThat(tick.tradeTime().toLocalDate())
                .isEqualTo(LocalDate.now(FIXED_CLOCK.withZone(KST)));
        assertThat(tick.tradeTime().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(tick.tradeTime().getHour()).isEqualTo(9);
        assertThat(tick.tradeTime().getMinute()).isEqualTo(30);
        assertThat(tick.tradeTime().getSecond()).isEqualTo(15);
    }

    @Test
    void tradeFrameMultipleItems() {
        StringBuilder data = new StringBuilder();
        for (int n = 0; n < 3; n++) {
            String[] f = new String[46];
            java.util.Arrays.fill(f, "0");
            f[0] = String.format("00000%d", n);
            f[1] = "100000";
            f[2] = String.valueOf(100 + n);
            f[12] = "1";
            f[13] = String.valueOf(10 + n);
            f[19] = String.valueOf(2 + n);
            f[20] = String.valueOf(3 + n);
            if (n > 0) {
                data.append("^");
            }
            data.append(String.join("^", f));
        }
        String raw = "0|H0STCNT0|003|" + data;

        DecodedFrame frame = decoder.decode(raw);
        assertThat(frame).isInstanceOf(TradeFrames.class);
        TradeFrames tf = (TradeFrames) frame;
        assertThat(tf.ticks()).hasSize(3);
        assertThat(tf.ticks().get(0).symbolCode()).isEqualTo("000000");
        assertThat(tf.ticks().get(1).symbolCode()).isEqualTo("000001");
        assertThat(tf.ticks().get(2).symbolCode()).isEqualTo("000002");
        assertThat(tf.ticks().get(0).price()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(tf.ticks().get(2).cumulativeVolume()).isEqualTo(12L);
        assertThat(tf.ticks().get(2).sellQty()).isEqualTo(4L);
        assertThat(tf.ticks().get(2).buyQty()).isEqualTo(5L);
    }

    @Test
    void orderBookFrameSingleItem() {
        String[] f = new String[59];
        java.util.Arrays.fill(f, "0");
        f[0] = "005930";
        f[1] = "093000";
        for (int j = 0; j < 10; j++) {
            f[3 + j] = String.valueOf(81000 + j);   // ASKP1..10
            f[13 + j] = String.valueOf(80900 - j);  // BIDP1..10
            f[23 + j] = String.valueOf(100 + j);    // ASKP_RSQN1..10
            f[33 + j] = String.valueOf(200 + j);    // BIDP_RSQN1..10
        }
        f[43] = "5000"; // TOTAL_ASKP_RSQN
        f[44] = "6000"; // TOTAL_BIDP_RSQN
        String raw = "0|H0STASP0|001|" + String.join("^", f);

        DecodedFrame frame = decoder.decode(raw);
        assertThat(frame).isInstanceOf(OrderBookFrames.class);
        OrderBookFrames ob = (OrderBookFrames) frame;
        assertThat(ob.books()).hasSize(1);

        var snapshot = ob.books().get(0).snapshot();
        assertThat(snapshot.symbolCode()).isEqualTo("005930");
        assertThat(snapshot.capturedAt().toLocalDate())
                .isEqualTo(LocalDate.now(FIXED_CLOCK.withZone(KST)));
        assertThat(snapshot.capturedAt().getHour()).isEqualTo(9);
        assertThat(snapshot.capturedAt().getMinute()).isEqualTo(30);
        assertThat(snapshot.capturedAt().getSecond()).isEqualTo(0);
        assertThat(snapshot.capturedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(snapshot.sourceName()).isEqualTo(KisWebSocketFrameDecoder.SOURCE_NAME);

        JsonNode payload = snapshot.payload();
        assertThat(payload.path("ask_prices").size()).isEqualTo(10);
        assertThat(payload.path("bid_prices").size()).isEqualTo(10);
        assertThat(payload.path("ask_volumes").size()).isEqualTo(10);
        assertThat(payload.path("bid_volumes").size()).isEqualTo(10);
        assertThat(payload.path("ask_prices").get(0).asLong()).isEqualTo(81000L);
        assertThat(payload.path("ask_prices").get(9).asLong()).isEqualTo(81009L);
        assertThat(payload.path("bid_prices").get(0).asLong()).isEqualTo(80900L);
        assertThat(payload.path("ask_volumes").get(5).asLong()).isEqualTo(105L);
        assertThat(payload.path("bid_volumes").get(5).asLong()).isEqualTo(205L);
        assertThat(payload.path("total_ask_volume").asLong()).isEqualTo(5000L);
        assertThat(payload.path("total_bid_volume").asLong()).isEqualTo(6000L);
    }

    @Test
    void encryptedFrameReturnsEncrypted() {
        String raw = "1|H0STCNT0|001|whatever";
        DecodedFrame frame = decoder.decode(raw);
        assertThat(frame).isInstanceOf(EncryptedFrame.class);
    }

    @Test
    void malformedTruncatedPipeFrameReturnsUnknown() {
        String raw = "0|H0STCNT0|";
        DecodedFrame frame = decoder.decode(raw);
        assertThat(frame).isInstanceOf(Unknown.class);
    }

    @Test
    void unknownTrIdReturnsUnknown() {
        String raw = "0|XXXXXXXX|001|" + String.join("^", new String[10]).replace(" ", "");
        DecodedFrame frame = decoder.decode(raw);
        assertThat(frame).isInstanceOf(Unknown.class);
    }

    @Test
    void emptyInputReturnsUnknown() {
        assertThat(decoder.decode("")).isInstanceOf(Unknown.class);
        assertThat(decoder.decode("   ")).isInstanceOf(Unknown.class);
        assertThat(decoder.decode(null)).isInstanceOf(Unknown.class);
    }
}
