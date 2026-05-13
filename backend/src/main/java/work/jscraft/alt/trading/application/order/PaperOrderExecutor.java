package work.jscraft.alt.trading.application.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemRepository;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService.PortfolioSnapshot;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.trading.domain.TradeOrderStatus;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

@Service
public class PaperOrderExecutor {

    public static final String EXECUTION_MODE_PAPER = "paper";
    public static final String ORDER_STATUS_FILLED = TradeOrderStatus.FILLED.wireValue();
    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";
    public static final String ORDER_TYPE_MARKET = "MARKET";
    public static final String ORDER_TYPE_LIMIT = "LIMIT";

    private final TradeOrderRepository tradeOrderRepository;
    private final MarketPriceItemRepository marketPriceItemRepository;
    private final PortfolioUpdateService portfolioUpdateService;
    private final Clock clock;

    public PaperOrderExecutor(
            TradeOrderRepository tradeOrderRepository,
            MarketPriceItemRepository marketPriceItemRepository,
            PortfolioUpdateService portfolioUpdateService,
            Clock clock) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.marketPriceItemRepository = marketPriceItemRepository;
        this.portfolioUpdateService = portfolioUpdateService;
        this.clock = clock;
    }

    @Transactional
    public List<TradeOrderEntity> execute(StrategyInstanceEntity instance, List<TradeOrderIntentEntity> intents) {
        List<TradeOrderEntity> orders = new ArrayList<>();
        for (TradeOrderIntentEntity intent : intents) {
            if (intent.getExecutionBlockedReason() != null) {
                continue;
            }
            BigDecimal fillPrice = resolveFillPrice(intent);
            PortfolioSnapshot snapshot = applyToPortfolio(instance.getId(), intent, fillPrice);
            TradeOrderEntity order = buildOrder(instance, intent, fillPrice, snapshot);
            orders.add(tradeOrderRepository.saveAndFlush(order));
        }
        return orders;
    }

    private BigDecimal resolveFillPrice(TradeOrderIntentEntity intent) {
        if (ORDER_TYPE_LIMIT.equals(intent.getOrderType())) {
            if (intent.getPrice() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "LIMIT 주문에 가격이 없습니다: intent=" + intent.getId());
            }
            return intent.getPrice();
        }
        return marketPriceItemRepository.findFirstBySymbolCodeOrderBySnapshotAtDesc(intent.getSymbolCode())
                .map(MarketPriceItemEntity::getLastPrice)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "MARKET 체결가를 찾을 수 없습니다: " + intent.getSymbolCode()));
    }

    private PortfolioSnapshot applyToPortfolio(UUID strategyInstanceId, TradeOrderIntentEntity intent, BigDecimal fillPrice) {
        if (SIDE_BUY.equals(intent.getSide())) {
            return portfolioUpdateService.applyBuy(strategyInstanceId, intent.getSymbolCode(),
                    intent.getQuantity(), fillPrice);
        }
        if (SIDE_SELL.equals(intent.getSide())) {
            return portfolioUpdateService.applySell(strategyInstanceId, intent.getSymbolCode(),
                    intent.getQuantity(), fillPrice);
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "지원하지 않는 side: " + intent.getSide());
    }

    private TradeOrderEntity buildOrder(
            StrategyInstanceEntity instance,
            TradeOrderIntentEntity intent,
            BigDecimal fillPrice,
            PortfolioSnapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        TradeOrderEntity order = new TradeOrderEntity();
        order.setTradeOrderIntent(intent);
        order.setStrategyInstance(instance);
        order.setClientOrderId("paper-" + UUID.randomUUID());
        order.setExecutionMode(EXECUTION_MODE_PAPER);
        order.setOrderStatus(ORDER_STATUS_FILLED);
        order.setRequestedQuantity(intent.getQuantity());
        order.setRequestedPrice(ORDER_TYPE_LIMIT.equals(intent.getOrderType()) ? intent.getPrice() : null);
        order.setFilledQuantity(intent.getQuantity());
        order.setAvgFilledPrice(fillPrice);
        order.setRequestedAt(now);
        order.setAcceptedAt(now);
        order.setFilledAt(now);
        order.setPortfolioAfterJson(portfolioUpdateService.toJson(snapshot));
        return order;
    }
}
