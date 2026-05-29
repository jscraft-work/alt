package work.jscraft.alt.trading.application.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.marketdata.OrderBookRedisCache;
import work.jscraft.alt.common.util.KrxTickSize;

/**
 * KIS WS push 호가 (Redis) 를 읽어 1~5 단계 잔량 누적 walk 시뮬레이션. 한 틱 양보 (BUY=+1tick, SELL=-1tick)
 * 를 평균 체결가에 적용해 paper 매수/매도의 "최소 보수성" 을 보장한다.
 *
 * <p>운영자 plan v3-minimal §1 spec 의 "단순히 KisWebSocketClient.subscribe 호출" 의 사후 호출처 —
 * PaperOrderExecutor 가 {@link #simulateBuy(String, BigDecimal)} / {@link #simulateSell(String, BigDecimal)} 호출.
 *
 * <p>운영자 spec 은 입력을 {@code (symbol, krwAmount)} 로 표기했으나, PaperOrderExecutor 가 LIMIT/MARKET
 * 양쪽 모두 {@code intent.quantity} 단위로 호출하는 것이 더 자연 (LIMIT 의 amount 가 LLM 지정가 × quantity
 * 와 일치 필수, MARKET 은 의도 amount 알 수 없음) — 그래서 입력을 {@code BigDecimal quantity} 로 통일.
 * walker 출력의 {@link WalkResult#actualGrossAmount}/{@link WalkResult#referencePrice} 를 PaperOrderExecutor 가
 * krwAmount 환산용으로 사용한다.
 */
@Component
public class PaperOrderbookWalkSimulator {

    public static final int MAX_WALK_LEVELS = 5;
    private static final int PRICE_SCALE = 6;
    private static final int RATIO_SCALE = 4;

    private final OrderBookRedisCache orderBookRedisCache;
    private final ObjectMapper objectMapper;

    public PaperOrderbookWalkSimulator(
            OrderBookRedisCache orderBookRedisCache,
            ObjectMapper objectMapper) {
        this.orderBookRedisCache = orderBookRedisCache;
        this.objectMapper = objectMapper;
    }

    public WalkResult simulateBuy(String symbolCode, BigDecimal requestedQuantity) {
        return walk(symbolCode, requestedQuantity, Side.BUY);
    }

    public WalkResult simulateSell(String symbolCode, BigDecimal requestedQuantity) {
        return walk(symbolCode, requestedQuantity, Side.SELL);
    }

    private WalkResult walk(String symbolCode, BigDecimal requestedQuantity, Side side) {
        if (symbolCode == null || symbolCode.isBlank()) {
            throw new IllegalArgumentException("symbolCode 가 비어 있습니다.");
        }
        if (requestedQuantity == null || requestedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("requestedQuantity 가 양수가 아닙니다: " + requestedQuantity);
        }

        String raw = orderBookRedisCache.read(symbolCode);
        if (raw == null) {
            throw new IllegalStateException("호가 캐시 없음: " + symbolCode);
        }
        JsonNode envelope = parse(raw, symbolCode);
        JsonNode payload = envelope.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            throw new IllegalStateException("호가 payload 없음: " + symbolCode);
        }

        ArrayNode prices = arrayOf(payload, side == Side.BUY ? "ask_prices" : "bid_prices", symbolCode);
        ArrayNode volumes = arrayOf(payload, side == Side.BUY ? "ask_volumes" : "bid_volumes", symbolCode);

        if (prices.size() == 0 || volumes.size() == 0) {
            throw new IllegalStateException("호가 비어 있음: " + symbolCode);
        }

        BigDecimal referencePrice = BigDecimal.valueOf(prices.get(0).asLong());

        // 1~5 단계 잔량 누적 walk
        BigDecimal cumulativeQty = BigDecimal.ZERO;
        BigDecimal cumulativeAmount = BigDecimal.ZERO;   // sum(levelPrice × usedQty), 한 틱 양보 전
        int walkLevels = 0;
        List<WalkStep> walkPath = new ArrayList<>();
        int levelsAvailable = Math.min(MAX_WALK_LEVELS, Math.min(prices.size(), volumes.size()));

        for (int k = 0; k < levelsAvailable; k++) {
            BigDecimal remaining = requestedQuantity.subtract(cumulativeQty);
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal levelPrice = BigDecimal.valueOf(prices.get(k).asLong());
            BigDecimal levelQty = BigDecimal.valueOf(volumes.get(k).asLong());
            if (levelPrice.signum() <= 0 || levelQty.signum() <= 0) {
                // KIS 가 호가 1 단계 비어있음(0) push 케이스 — 그 단계 사용 0, 다음 단계로
                walkLevels = k + 1;
                walkPath.add(new WalkStep(k + 1, levelPrice, levelQty, BigDecimal.ZERO));
                continue;
            }

            BigDecimal usedQty = remaining.min(levelQty);
            cumulativeQty = cumulativeQty.add(usedQty);
            cumulativeAmount = cumulativeAmount.add(levelPrice.multiply(usedQty));
            walkLevels = k + 1;
            walkPath.add(new WalkStep(k + 1, levelPrice, levelQty, usedQty));
        }

        // 평균 체결가 + 한 틱 양보 적용 (해석 A 확정 — team-lead Day 2)
        BigDecimal avgBeforeTick;
        BigDecimal avgFilledPrice;
        BigDecimal tickConcession;
        if (cumulativeQty.signum() == 0) {
            avgBeforeTick = BigDecimal.ZERO;
            avgFilledPrice = BigDecimal.ZERO;
            tickConcession = BigDecimal.ZERO;
        } else {
            avgBeforeTick = cumulativeAmount.divide(cumulativeQty, PRICE_SCALE, RoundingMode.HALF_UP);
            tickConcession = KrxTickSize.tickSize(avgBeforeTick);
            avgFilledPrice = side == Side.BUY
                    ? avgBeforeTick.add(tickConcession)
                    : avgBeforeTick.subtract(tickConcession);
        }

        BigDecimal filledQuantity = cumulativeQty;
        BigDecimal unfilledQuantity = requestedQuantity.subtract(cumulativeQty).max(BigDecimal.ZERO);
        BigDecimal partialFillRatio = filledQuantity.divide(requestedQuantity, RATIO_SCALE, RoundingMode.HALF_UP);
        BigDecimal slippageAmount = avgFilledPrice.subtract(referencePrice).abs().multiply(filledQuantity);
        BigDecimal actualGrossAmount = avgFilledPrice.multiply(filledQuantity);

        JsonNode snapshotJson = buildSnapshotJson(
                side, prices, volumes, walkPath,
                referencePrice, avgBeforeTick, tickConcession, avgFilledPrice,
                envelope.path("capturedAt").asText(""));

        return new WalkResult(
                referencePrice, avgFilledPrice, filledQuantity, unfilledQuantity,
                walkLevels, partialFillRatio, slippageAmount, actualGrossAmount, snapshotJson);
    }

    private JsonNode parse(String raw, String symbolCode) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("호가 JSON 파싱 실패: " + symbolCode, ex);
        }
    }

    private ArrayNode arrayOf(JsonNode payload, String fieldName, String symbolCode) {
        JsonNode node = payload.path(fieldName);
        if (!node.isArray()) {
            throw new IllegalStateException("호가 payload." + fieldName + " 가 배열이 아닙니다: " + symbolCode);
        }
        return (ArrayNode) node;
    }

    private JsonNode buildSnapshotJson(
            Side side,
            ArrayNode prices,
            ArrayNode volumes,
            List<WalkStep> walkPath,
            BigDecimal referencePrice,
            BigDecimal avgBeforeTick,
            BigDecimal tickConcession,
            BigDecimal avgFilledPrice,
            String capturedAt) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("side", side.name());
        if (!capturedAt.isEmpty()) {
            node.put("captured_at", capturedAt);
        }
        node.put("reference_price", referencePrice);
        node.put("avg_filled_price_before_tick", avgBeforeTick);
        node.put("tick_concession", tickConcession);
        node.put("avg_filled_price", avgFilledPrice);

        ArrayNode levelsNode = node.putArray("available_levels");
        int levels = Math.min(MAX_WALK_LEVELS, Math.min(prices.size(), volumes.size()));
        for (int k = 0; k < levels; k++) {
            ObjectNode lvl = objectMapper.createObjectNode();
            lvl.put("level", k + 1);
            lvl.put("price", prices.get(k).asLong());
            lvl.put("qty", volumes.get(k).asLong());
            levelsNode.add(lvl);
        }

        ArrayNode pathNode = node.putArray("walk_path");
        for (WalkStep step : walkPath) {
            ObjectNode p = objectMapper.createObjectNode();
            p.put("level", step.level());
            p.put("price", step.price());
            p.put("available_qty", step.availableQty());
            p.put("used_qty", step.usedQty());
            pathNode.add(p);
        }

        return node;
    }

    private enum Side {
        BUY, SELL
    }

    private record WalkStep(
            int level,
            BigDecimal price,
            BigDecimal availableQty,
            BigDecimal usedQty) {
    }
}
