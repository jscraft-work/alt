package work.jscraft.alt.integrations.kis.broker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import work.jscraft.alt.trading.application.broker.AccountStatus;
import work.jscraft.alt.trading.application.broker.AccountStatus.HoldingItem;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException.Category;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;

@Component
public class KisResponseMapper {

    static final String VENDOR = "kis";

    private final ObjectMapper objectMapper;

    public KisResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlaceOrderResult mapPlaceOrderResponse(String clientOrderId, String responseBody) {
        JsonNode root = parseJson(responseBody);
        String rtCd = root.path("rt_cd").asText("");
        String msg = root.path("msg1").asText("");
        if (!"0".equals(rtCd)) {
            String reason = msg.isBlank() ? ("KIS rt_cd=" + rtCd) : msg;
            return new PlaceOrderResult(
                    clientOrderId,
                    null,
                    PlaceOrderResult.STATUS_REJECTED,
                    BigDecimal.ZERO,
                    null,
                    reason);
        }
        JsonNode output = root.path("output");
        String brokerOrderNo = textOrEmpty(output, "ODNO");
        BigDecimal filledQty = decimalOrZero(output, "tot_ccld_qty");
        BigDecimal avgPrice = decimalOrNull(output, "avg_prvs");
        String status = output.has("ord_stat") ? output.path("ord_stat").asText() : null;
        if (status == null || status.isBlank()) {
            status = decideStatusFromFill(filledQty, output);
        }
        return new PlaceOrderResult(clientOrderId, brokerOrderNo, status, filledQty, avgPrice, null);
    }

    public OrderStatusResult mapOrderStatusResponse(String responseBody) {
        JsonNode root = parseJson(responseBody);
        String rtCd = root.path("rt_cd").asText("");
        if (!"0".equals(rtCd)) {
            throw new BrokerGatewayException(
                    Category.INVALID_RESPONSE,
                    VENDOR,
                    "주문 상태 조회 실패 rt_cd=" + rtCd + " msg=" + root.path("msg1").asText(""));
        }
        JsonNode output = root.path("output");
        String brokerOrderNo = textOrEmpty(output, "ODNO");
        BigDecimal filledQty = decimalOrZero(output, "tot_ccld_qty");
        BigDecimal totalQty = decimalOrZero(output, "ord_qty");
        BigDecimal avgPrice = decimalOrNull(output, "avg_prvs");
        String status;
        if (output.has("ord_stat") && !output.get("ord_stat").asText("").isBlank()) {
            status = output.get("ord_stat").asText();
        } else if (filledQty.signum() == 0) {
            status = PlaceOrderResult.STATUS_ACCEPTED;
        } else if (filledQty.compareTo(totalQty) >= 0 && totalQty.signum() > 0) {
            status = PlaceOrderResult.STATUS_FILLED;
        } else {
            status = PlaceOrderResult.STATUS_PARTIAL;
        }
        String failureReason = null;
        if (PlaceOrderResult.STATUS_REJECTED.equals(status) || PlaceOrderResult.STATUS_FAILED.equals(status)) {
            failureReason = output.path("rjct_rson").asText(null);
        }
        return new OrderStatusResult(brokerOrderNo, status, filledQty, avgPrice, failureReason);
    }

    public AccountStatus mapAccountStatusResponse(String responseBody) {
        JsonNode root = parseJson(responseBody);
        String rtCd = root.path("rt_cd").asText("");
        if (!"0".equals(rtCd)) {
            throw new BrokerGatewayException(
                    Category.INVALID_RESPONSE,
                    VENDOR,
                    "계좌 조회 실패 rt_cd=" + rtCd);
        }
        JsonNode output = root.path("output");
        BigDecimal cashAmount = decimalOrZero(output, "dnca_tot_amt");
        BigDecimal totalAssetAmount = decimalOrZero(output, "tot_evlu_amt");
        List<HoldingItem> holdings = new ArrayList<>();
        JsonNode positions = root.path("positions");
        if (positions.isArray()) {
            for (JsonNode pos : positions) {
                String symbolCode = textOrEmpty(pos, "pdno");
                BigDecimal qty = decimalOrZero(pos, "hldg_qty");
                BigDecimal avgBuyPrice = decimalOrZero(pos, "pchs_avg_pric");
                if (!symbolCode.isBlank()) {
                    holdings.add(new HoldingItem(symbolCode, qty, avgBuyPrice));
                }
            }
        }
        return new AccountStatus(cashAmount, totalAssetAmount, holdings);
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            throw new BrokerGatewayException(Category.EMPTY_RESPONSE, VENDOR, "KIS 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new BrokerGatewayException(Category.INVALID_RESPONSE, VENDOR,
                    "KIS 응답 JSON 파싱 실패: " + ex.getOriginalMessage(), ex);
        }
    }

    private String textOrEmpty(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : "";
    }

    private BigDecimal decimalOrZero(JsonNode node, String field) {
        BigDecimal value = decimalOrNull(node, field);
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String text = node.get(field).asText("").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String decideStatusFromFill(BigDecimal filledQty, JsonNode output) {
        BigDecimal totalQty = decimalOrZero(output, "ord_qty");
        if (filledQty.signum() == 0) {
            return PlaceOrderResult.STATUS_ACCEPTED;
        }
        if (filledQty.compareTo(totalQty) >= 0 && totalQty.signum() > 0) {
            return PlaceOrderResult.STATUS_FILLED;
        }
        return PlaceOrderResult.STATUS_PARTIAL;
    }

    // -----------------------------------------------------------------
    // Helpers consumed by KisBrokerAdapter (KIS REST 본구현 진입점).
    // -----------------------------------------------------------------

    /**
     * 토큰 만료/인증 오류 여부 판단. KIS 가이드 기준 {@code EGW00121}, {@code EGW00123},
     * msg1에 "토큰" 키워드가 포함된 응답을 인증 실패로 분류한다.
     */
    public static boolean isAuthError(String msgCd, String msg1) {
        String code = msgCd == null ? "" : msgCd.trim();
        if ("EGW00121".equals(code) || "EGW00123".equals(code)) {
            return true;
        }
        return msg1 != null && msg1.contains("토큰");
    }

    /**
     * rt_cd != "0" 응답을 카테고리에 매핑. 인증 키워드를 만나면 {@code AUTH_FAILED},
     * 그 외는 {@code TRANSIENT}.
     */
    public static BrokerGatewayException.Category classifyKisError(String rtCd, String msgCd, String msg1) {
        if (rtCd == null || rtCd.isBlank() || "0".equals(rtCd.trim())) {
            return null;
        }
        if (isAuthError(msgCd, msg1)) {
            return BrokerGatewayException.Category.AUTH_FAILED;
        }
        return BrokerGatewayException.Category.TRANSIENT;
    }

    /**
     * 문자열 BigDecimal 파싱. 빈/누락 노드는 null. 잘못된 숫자 포맷은 INVALID_RESPONSE.
     */
    public static BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new BrokerGatewayException(
                    BrokerGatewayException.Category.INVALID_RESPONSE,
                    VENDOR,
                    "KIS 숫자 응답 파싱 실패 raw_len=" + raw.length(), ex);
        }
    }

    /** {@link #parseDecimal(JsonNode)}와 같으나 수량 의도를 표현. */
    public static BigDecimal parseQuantity(JsonNode node) {
        return parseDecimal(node);
    }

    /** 비교용 안전한 텍스트 추출 — null/empty 노드는 빈 문자열. */
    public static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }
}
