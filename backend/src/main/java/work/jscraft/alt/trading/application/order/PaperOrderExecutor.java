package work.jscraft.alt.trading.application.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.common.util.KrxTickSize;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService.PortfolioSnapshot;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.application.ops.PaperTradeMatcher;
import work.jscraft.alt.trading.domain.TradeOrderStatus;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

/**
 * paper 모드 주문 실행 — V17 비용 breakdown + V18 paper_trade_match 의 source.
 *
 * <p>Day 3 변경 (M1):
 * <ol>
 *   <li>{@link PaperOrderbookWalkSimulator} 호출 — KIS WS 호가 5 단계 walk + 한 틱 양보</li>
 *   <li>LIMIT 가격은 {@link KrxTickSize} 로 호가단위 정합화 (BUY=내림, SELL=올림)</li>
 *   <li>fullyUnfilled → trade_order 안 만들고 {@code execution_blocked_reason='paper_orderbook_walk_insufficient'}</li>
 *   <li>MARKET 의 trade_order.requested_price = walker.referencePrice (= 매도1 BUY / 매수1 SELL)</li>
 *   <li>V17 의 paper_* 9 컬럼 적재 (amount 기반 비용 5 + walk 결과 4)</li>
 *   <li>invariant: portfolio.cash 변동 = paper_actual_amount → {@link PortfolioUpdateService#applyBuyWithCost}
 *       / {@link PortfolioUpdateService#applySellWithCost} 가 정확히 cashOut/cashIn = paper_actual_amount 받음</li>
 *   <li>SELL 체결 시 {@link PaperTradeMatcher#onSellFilled} 호출 (Day 4 에 FIFO 매칭 + paper_trade_match INSERT)</li>
 *   <li>부분체결 시 {@code unfilled_quantity} 컬럼 적재, status 는 FILLED (paper 의 paper_partial_fill_ratio 가 부분 표현)</li>
 * </ol>
 *
 * <p>비용 상수 (운영자가 한투 약정 확인 후 변경 가능):
 * <ul>
 *   <li>{@link #SELL_TAX_RATE} = 0.15% (KOSPI 농특세 / KOSDAQ 거래세 동일)</li>
 *   <li>{@link #COMMISSION_RATE} = 0.014% (한투 비대면 양쪽 적용)</li>
 * </ul>
 */
@Service
public class PaperOrderExecutor {

    public static final String EXECUTION_MODE_PAPER = "paper";
    public static final String ORDER_STATUS_FILLED = TradeOrderStatus.FILLED.wireValue();
    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";
    public static final String ORDER_TYPE_MARKET = "MARKET";
    public static final String ORDER_TYPE_LIMIT = "LIMIT";
    public static final String BLOCKED_REASON_WALK_INSUFFICIENT = "paper_orderbook_walk_insufficient";

    public static final BigDecimal SELL_TAX_RATE = new BigDecimal("0.0015");      // 0.15%
    public static final BigDecimal COMMISSION_RATE = new BigDecimal("0.00014");   // 0.014%
    private static final int AMOUNT_SCALE = 4;

    private static final Logger log = LoggerFactory.getLogger(PaperOrderExecutor.class);

    private final TradeOrderRepository tradeOrderRepository;
    private final TradeOrderIntentRepository tradeOrderIntentRepository;
    private final PortfolioUpdateService portfolioUpdateService;
    private final PaperOrderbookWalkSimulator walkSimulator;
    private final PaperTradeMatcher paperTradeMatcher;
    private final Clock clock;

    public PaperOrderExecutor(
            TradeOrderRepository tradeOrderRepository,
            TradeOrderIntentRepository tradeOrderIntentRepository,
            PortfolioUpdateService portfolioUpdateService,
            PaperOrderbookWalkSimulator walkSimulator,
            PaperTradeMatcher paperTradeMatcher,
            Clock clock) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.tradeOrderIntentRepository = tradeOrderIntentRepository;
        this.portfolioUpdateService = portfolioUpdateService;
        this.walkSimulator = walkSimulator;
        this.paperTradeMatcher = paperTradeMatcher;
        this.clock = clock;
    }

    @Transactional
    public List<TradeOrderEntity> execute(StrategyInstanceEntity instance, List<TradeOrderIntentEntity> intents) {
        List<TradeOrderEntity> orders = new ArrayList<>();
        for (TradeOrderIntentEntity intent : intents) {
            if (intent.getExecutionBlockedReason() != null) {
                continue;
            }

            String side = intent.getSide();
            BigDecimal requestedQuantity = intent.getQuantity();
            if (requestedQuantity == null || requestedQuantity.signum() <= 0) {
                blockIntent(intent, BLOCKED_REASON_WALK_INSUFFICIENT,
                        "intent.quantity 비정상: " + requestedQuantity);
                continue;
            }

            WalkResult walk;
            try {
                walk = SIDE_BUY.equals(side)
                        ? walkSimulator.simulateBuy(intent.getSymbolCode(), requestedQuantity)
                        : walkSimulator.simulateSell(intent.getSymbolCode(), requestedQuantity);
            } catch (IllegalStateException ex) {
                log.warn("paper 호가 walk 실패 intent={} symbol={} reason={}",
                        intent.getId(), intent.getSymbolCode(), ex.getMessage());
                blockIntent(intent, BLOCKED_REASON_WALK_INSUFFICIENT, ex.getMessage());
                continue;
            }

            if (walk.isFullyUnfilled()) {
                log.warn("paper 호가 walk 잔량 부족 intent={} symbol={} requested={}",
                        intent.getId(), intent.getSymbolCode(), requestedQuantity);
                blockIntent(intent, BLOCKED_REASON_WALK_INSUFFICIENT,
                        "walk 5 단계 후 fillable 잔량 0");
                continue;
            }

            BigDecimal requestedPrice = resolveRequestedPrice(intent, walk);
            BigDecimal filledQuantity = walk.filledQuantity();
            BigDecimal avgFilledPrice = walk.avgFilledPrice();
            BigDecimal grossFillAmount = avgFilledPrice.multiply(filledQuantity);

            BigDecimal paperRequestedAmount = requestedPrice.multiply(filledQuantity)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            // signed: BUY 양수 = 비싸게 잡힘 (불리), SELL 양수 = 비싸게 팔림 (운 좋음).
            BigDecimal paperSlippageAmount = avgFilledPrice.subtract(requestedPrice)
                    .multiply(filledQuantity)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal paperSellTaxAmount = SIDE_SELL.equals(side)
                    ? grossFillAmount.multiply(SELL_TAX_RATE).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(AMOUNT_SCALE);
            BigDecimal paperCommissionAmount = grossFillAmount.multiply(COMMISSION_RATE)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

            // paper_actual_amount = cash 변동 절대값 — grossFill (= avg × qty) 기반 직접 계산
            BigDecimal paperActualAmount;
            if (SIDE_BUY.equals(side)) {
                paperActualAmount = grossFillAmount.add(paperCommissionAmount)
                        .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            } else {
                paperActualAmount = grossFillAmount.subtract(paperSellTaxAmount).subtract(paperCommissionAmount)
                        .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            }

            // invariant self-check — signed slippage 정의 하에서 식이 항상 정합
            //   BUY:  actual = requested + slippage + commission
            //   SELL: actual = requested + slippage - sell_tax - commission
            BigDecimal expectedFromFormula = SIDE_BUY.equals(side)
                    ? paperRequestedAmount.add(paperSlippageAmount).add(paperCommissionAmount)
                            .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                    : paperRequestedAmount.add(paperSlippageAmount)
                            .subtract(paperSellTaxAmount).subtract(paperCommissionAmount)
                            .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            if (expectedFromFormula.compareTo(paperActualAmount) != 0) {
                throw new IllegalStateException(
                        "paper 비용 invariant 위반: intent=" + intent.getId()
                                + " side=" + side
                                + " expected=" + expectedFromFormula
                                + " actual=" + paperActualAmount);
            }

            PortfolioSnapshot snapshot;
            if (SIDE_BUY.equals(side)) {
                snapshot = portfolioUpdateService.applyBuyWithCost(
                        instance.getId(), intent.getSymbolCode(),
                        filledQuantity, avgFilledPrice, paperActualAmount);
            } else if (SIDE_SELL.equals(side)) {
                snapshot = portfolioUpdateService.applySellWithCost(
                        instance.getId(), intent.getSymbolCode(),
                        filledQuantity, avgFilledPrice, paperActualAmount);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "지원하지 않는 side: " + side);
            }

            BigDecimal unfilledQuantity = walk.unfilledQuantity();
            TradeOrderEntity order = buildOrder(
                    instance, intent, side,
                    requestedQuantity, requestedPrice,
                    filledQuantity, avgFilledPrice, unfilledQuantity,
                    paperRequestedAmount, paperSlippageAmount,
                    paperSellTaxAmount, paperCommissionAmount, paperActualAmount,
                    walk, snapshot);

            TradeOrderEntity saved = tradeOrderRepository.saveAndFlush(order);

            if (SIDE_SELL.equals(side)) {
                paperTradeMatcher.onSellFilled(saved);
            }

            orders.add(saved);
        }
        return orders;
    }

    /**
     * LIMIT 의 경우 intent.price 를 호가단위에 맞춰 정합 (BUY=내림, SELL=올림).
     * MARKET 의 경우 walker.referencePrice (= 매도1 BUY / 매수1 SELL) 를 그대로 사용.
     */
    private BigDecimal resolveRequestedPrice(TradeOrderIntentEntity intent, WalkResult walk) {
        if (ORDER_TYPE_LIMIT.equals(intent.getOrderType())) {
            if (intent.getPrice() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "LIMIT 주문에 가격이 없습니다: intent=" + intent.getId());
            }
            return SIDE_BUY.equals(intent.getSide())
                    ? KrxTickSize.roundDownToTick(intent.getPrice())
                    : KrxTickSize.roundUpToTick(intent.getPrice());
        }
        if (walk.referencePrice() == null || walk.referencePrice().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "MARKET 의 walker referencePrice 가 0 또는 null 입니다: intent=" + intent.getId());
        }
        return walk.referencePrice();
    }

    private TradeOrderEntity buildOrder(
            StrategyInstanceEntity instance,
            TradeOrderIntentEntity intent,
            String side,
            BigDecimal requestedQuantity,
            BigDecimal requestedPrice,
            BigDecimal filledQuantity,
            BigDecimal avgFilledPrice,
            BigDecimal unfilledQuantity,
            BigDecimal paperRequestedAmount,
            BigDecimal paperSlippageAmount,
            BigDecimal paperSellTaxAmount,
            BigDecimal paperCommissionAmount,
            BigDecimal paperActualAmount,
            WalkResult walk,
            PortfolioSnapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("paper-" + UUID.randomUUID());
        order.setExecutionMode(EXECUTION_MODE_PAPER);
        // paper 부분체결도 FILLED 마킹 + paper_partial_fill_ratio / unfilled_quantity 로 부분 표현
        order.setOrderStatus(ORDER_STATUS_FILLED);
        order.setRequestedQuantity(requestedQuantity);
        order.setRequestedPrice(requestedPrice);
        order.setFilledQuantity(filledQuantity);
        order.setAvgFilledPrice(avgFilledPrice);
        order.setRequestedAt(now);
        order.setAcceptedAt(now);
        order.setFilledAt(now);
        order.setPortfolioAfterJson(portfolioUpdateService.toJson(snapshot));

        // V17 비용 breakdown
        order.setPaperRequestedAmount(paperRequestedAmount);
        order.setPaperSlippageAmount(paperSlippageAmount);
        order.setPaperSellTaxAmount(paperSellTaxAmount);
        order.setPaperCommissionAmount(paperCommissionAmount);
        order.setPaperActualAmount(paperActualAmount);

        // V17 walk 결과
        order.setPaperWalkLevels((short) walk.walkLevels());
        order.setPaperPartialFillRatio(walk.partialFillRatio());
        order.setPaperOrderbookSnapshotJson(walk.orderbookSnapshotJson());
        order.setUnfilledQuantity(unfilledQuantity);

        return order;
    }

    private void blockIntent(TradeOrderIntentEntity intent, String reason, String detail) {
        intent.setExecutionBlockedReason(reason);
        tradeOrderIntentRepository.saveAndFlush(intent);
        log.info("paper intent blocked id={} symbol={} reason={} detail={}",
                intent.getId(), intent.getSymbolCode(), reason, detail);
    }
}
