package work.jscraft.alt.trading.application.decision;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.springframework.stereotype.Component;

import work.jscraft.alt.trading.application.decision.ParsedDecision.BoxEstimate;
import work.jscraft.alt.trading.application.decision.ParsedDecision.ParsedOrder;

@Component
public class DecisionParser {

    public static final String STATUS_EXECUTE = "EXECUTE";
    public static final String STATUS_HOLD = "HOLD";

    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_EXECUTE, STATUS_HOLD);
    private static final Set<String> ALLOWED_SIDES = Set.of("BUY", "SELL");
    private static final Set<String> ALLOWED_ORDER_TYPES = Set.of("MARKET", "LIMIT");

    private final ObjectMapper objectMapper;
    private final ObjectReader lenientReader;

    public DecisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // LLM이 rationale 같은 string value 안에 escape 안 된 raw newline을 흘려서 응답하는
        // 케이스가 흔하다. 표준 JSON은 거부지만 운영적으로 관대하게 받아 둔다.
        this.lenientReader = objectMapper.reader()
                .with(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS);
    }

    public ParsedDecision parse(String stdoutText) {
        if (stdoutText == null || stdoutText.isBlank()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_JSON, "응답 본문이 비어 있습니다.");
        }
        JsonNode root;
        try {
            root = lenientReader.readTree(stdoutText);
        } catch (JsonProcessingException ex) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_JSON,
                    "JSON 파싱 실패: " + ex.getOriginalMessage(),
                    ex);
        }
        if (!root.isObject()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_JSON, "JSON 객체가 아닙니다.");
        }

        String cycleStatus = requireText(root, "cycleStatus");
        if (!ALLOWED_STATUSES.contains(cycleStatus)) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_STATUS,
                    "허용되지 않는 cycleStatus: " + cycleStatus);
        }
        String summary = root.path("summary").asText("");
        BigDecimal confidence = root.has("confidence") && !root.get("confidence").isNull()
                ? new BigDecimal(root.get("confidence").asText())
                : null;
        BoxEstimate boxEstimate = parseBoxEstimate(root);

        List<ParsedOrder> orders = parseOrders(root);

        if (STATUS_HOLD.equals(cycleStatus) && !orders.isEmpty()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.CONTENT_CONFLICT,
                    "HOLD인데 orders가 비어있지 않습니다.");
        }
        if (STATUS_EXECUTE.equals(cycleStatus) && orders.isEmpty()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.CONTENT_CONFLICT,
                    "EXECUTE인데 orders가 비어 있습니다.");
        }

        return new ParsedDecision(cycleStatus, summary, confidence, boxEstimate, orders);
    }

    private BoxEstimate parseBoxEstimate(JsonNode root) {
        JsonNode node = root.path("boxEstimate");
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_JSON,
                    "boxEstimate는 객체여야 합니다.");
        }
        BigDecimal low = readOptionalDecimal(node, "low");
        BigDecimal high = readOptionalDecimal(node, "high");
        BigDecimal confidence = readOptionalDecimal(node, "confidence");
        if (low == null && high == null && confidence == null) {
            return null;
        }
        return new BoxEstimate(low, high, confidence);
    }

    private BigDecimal readOptionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_JSON,
                    "boxEstimate." + field + " 숫자 형식 오류");
        }
    }

    private List<ParsedOrder> parseOrders(JsonNode root) {
        JsonNode ordersNode = root.path("orders");
        if (ordersNode.isMissingNode() || ordersNode.isNull()) {
            return List.of();
        }
        if (!ordersNode.isArray()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_ORDER, "orders는 배열이어야 합니다.");
        }
        List<ParsedOrder> parsed = new ArrayList<>();
        int index = 0;
        for (JsonNode node : ordersNode) {
            index++;
            if (!node.isObject()) {
                throw new DecisionParseException(
                        DecisionParseException.Reason.INVALID_ORDER,
                        "orders[" + (index - 1) + "]는 객체여야 합니다.");
            }
            int sequenceNo = node.has("sequenceNo") ? node.get("sequenceNo").asInt(index) : index;
            String symbolCode = requireText(node, "symbolCode");
            String side = requireText(node, "side");
            if (!ALLOWED_SIDES.contains(side)) {
                throw new DecisionParseException(
                        DecisionParseException.Reason.INVALID_ORDER, "허용되지 않는 side: " + side);
            }
            String orderType = requireText(node, "orderType");
            if (!ALLOWED_ORDER_TYPES.contains(orderType)) {
                throw new DecisionParseException(
                        DecisionParseException.Reason.INVALID_ORDER, "허용되지 않는 orderType: " + orderType);
            }
            BigDecimal quantity = readDecimalRequired(node, "quantity");
            BigDecimal price = readDecimal(node, "price");
            String rationale = node.path("rationale").asText("");
            JsonNode evidence = node.has("evidence") && !node.get("evidence").isNull()
                    ? node.get("evidence")
                    : objectMapper.createArrayNode();
            parsed.add(new ParsedOrder(sequenceNo, symbolCode, side, quantity, orderType, price, rationale, evidence));
        }
        return parsed;
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.MISSING_FIELD, "필수 필드 누락: " + field);
        }
        return value.asText();
    }

    private BigDecimal readDecimalRequired(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.MISSING_FIELD, "필수 필드 누락: " + field);
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_ORDER, field + " 숫자 형식 오류");
        }
    }

    private BigDecimal readDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            throw new DecisionParseException(
                    DecisionParseException.Reason.INVALID_ORDER, field + " 숫자 형식 오류");
        }
    }
}
