package work.jscraft.alt;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.trading.application.decision.DecisionParseException;
import work.jscraft.alt.trading.application.decision.DecisionParser;
import work.jscraft.alt.trading.application.decision.ParsedDecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionParserValidationTest {

    private final DecisionParser parser = new DecisionParser(new ObjectMapper());

    @Test
    void parsesHoldStatusWithoutOrders() {
        String json = """
                {"cycleStatus":"HOLD","summary":"관망","confidence":0.42}
                """;

        ParsedDecision decision = parser.parse(json);

        assertThat(decision.cycleStatus()).isEqualTo("HOLD");
        assertThat(decision.summary()).isEqualTo("관망");
        assertThat(decision.confidence()).isEqualByComparingTo("0.42");
        assertThat(decision.orders()).isEmpty();
    }

    @Test
    void parsesExecuteWithOrders() {
        String json = """
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"삼성전자 매수",
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":5,"orderType":"LIMIT","price":81000,"rationale":"실적 기대"}
                  ]
                }
                """;

        ParsedDecision decision = parser.parse(json);

        assertThat(decision.cycleStatus()).isEqualTo("EXECUTE");
        assertThat(decision.orders()).hasSize(1);
        ParsedDecision.ParsedOrder order = decision.orders().get(0);
        assertThat(order.symbolCode()).isEqualTo("005930");
        assertThat(order.side()).isEqualTo("BUY");
        assertThat(order.orderType()).isEqualTo("LIMIT");
        assertThat(order.price()).isEqualByComparingTo("81000");
        assertThat(order.quantity()).isEqualByComparingTo("5");
    }

    @Test
    void emptyOrInvalidJsonRaisesInvalidJson() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.INVALID_JSON);

        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.INVALID_JSON);
    }

    @Test
    void missingCycleStatusFieldRaisesMissingField() {
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"x\"}"))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.MISSING_FIELD);
    }

    @Test
    void disallowedStatusRaisesInvalidStatus() {
        assertThatThrownBy(() -> parser.parse("{\"cycleStatus\":\"PANIC\"}"))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.INVALID_STATUS);
    }

    @Test
    void holdWithOrdersRaisesContentConflict() {
        String json = """
                {"cycleStatus":"HOLD","orders":[{"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":1,"orderType":"MARKET"}]}
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.CONTENT_CONFLICT);
    }

    @Test
    void executeWithoutOrdersRaisesContentConflict() {
        String json = "{\"cycleStatus\":\"EXECUTE\"}";
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.CONTENT_CONFLICT);
    }

    @Test
    void invalidSideOrOrderTypeRaisesInvalidOrder() {
        String badSide = """
                {"cycleStatus":"EXECUTE","orders":[{"sequenceNo":1,"symbolCode":"005930","side":"SHORT","quantity":1,"orderType":"MARKET"}]}
                """;
        assertThatThrownBy(() -> parser.parse(badSide))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.INVALID_ORDER);

        String badType = """
                {"cycleStatus":"EXECUTE","orders":[{"sequenceNo":1,"symbolCode":"005930","side":"BUY","quantity":1,"orderType":"STOP"}]}
                """;
        assertThatThrownBy(() -> parser.parse(badType))
                .isInstanceOf(DecisionParseException.class)
                .extracting(ex -> ((DecisionParseException) ex).reason())
                .isEqualTo(DecisionParseException.Reason.INVALID_ORDER);
    }
}
