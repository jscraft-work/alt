package work.jscraft.alt.trading.application.decision;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketPriceItemRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.trading.application.order.PaperOrderExecutor;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderIntentRepository;

@Service
public class OrderIntentSafetyValidator {

    public static final String REASON_QUANTITY_NON_POSITIVE = "quantity_non_positive";
    public static final String REASON_LIMIT_PRICE_MISSING = "limit_price_missing";
    public static final String REASON_LIMIT_PRICE_NON_POSITIVE = "limit_price_non_positive";
    public static final String REASON_MARKET_PRICE_NOT_ALLOWED = "market_price_not_allowed";
    public static final String REASON_MARKET_PRICE_UNAVAILABLE = "market_price_unavailable";
    public static final String REASON_INSUFFICIENT_CASH = "insufficient_cash";
    public static final String REASON_INSUFFICIENT_POSITION = "insufficient_position";

    private final TradeOrderIntentRepository tradeOrderIntentRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final MarketPriceItemRepository marketPriceItemRepository;

    public OrderIntentSafetyValidator(
            TradeOrderIntentRepository tradeOrderIntentRepository,
            PortfolioRepository portfolioRepository,
            PortfolioPositionRepository portfolioPositionRepository,
            MarketPriceItemRepository marketPriceItemRepository) {
        this.tradeOrderIntentRepository = tradeOrderIntentRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.marketPriceItemRepository = marketPriceItemRepository;
    }

    @Transactional
    public List<TradeOrderIntentEntity> validate(
            UUID strategyInstanceId,
            String executionMode,
            List<TradeOrderIntentEntity> intents) {
        BigDecimal cash = portfolioRepository.findByStrategyInstanceId(strategyInstanceId)
                .map(PortfolioEntity::getCashAmount)
                .orElse(BigDecimal.ZERO);
        Map<String, BigDecimal> positionBySymbol = new HashMap<>();
        for (PortfolioPositionEntity position
                : portfolioPositionRepository.findByStrategyInstanceId(strategyInstanceId)) {
            positionBySymbol.put(position.getSymbolCode(), position.getQuantity());
        }

        for (TradeOrderIntentEntity intent : intents) {
            BigDecimal effectivePrice = resolveEffectivePrice(intent, executionMode);
            String blockedReason = evaluate(
                    intent,
                    cash,
                    positionBySymbol,
                    effectivePrice,
                    PaperOrderExecutor.EXECUTION_MODE_PAPER.equals(executionMode));
            if (blockedReason != null) {
                intent.setExecutionBlockedReason(blockedReason);
                tradeOrderIntentRepository.saveAndFlush(intent);
                continue;
            }
            if ("BUY".equals(intent.getSide()) && effectivePrice != null) {
                cash = cash.subtract(effectivePrice.multiply(intent.getQuantity()));
            } else if ("SELL".equals(intent.getSide())) {
                BigDecimal current = positionBySymbol.getOrDefault(intent.getSymbolCode(), BigDecimal.ZERO);
                positionBySymbol.put(intent.getSymbolCode(), current.subtract(intent.getQuantity()));
            }
        }
        return intents;
    }

    private String evaluate(
            TradeOrderIntentEntity intent,
            BigDecimal cashRemaining,
            Map<String, BigDecimal> positionBySymbol,
            BigDecimal effectivePrice,
            boolean requireMarketPrice) {
        if (intent.getQuantity() == null || intent.getQuantity().signum() <= 0) {
            return REASON_QUANTITY_NON_POSITIVE;
        }
        String orderType = intent.getOrderType();
        BigDecimal price = intent.getPrice();
        if ("LIMIT".equals(orderType)) {
            if (price == null) {
                return REASON_LIMIT_PRICE_MISSING;
            }
            if (price.signum() <= 0) {
                return REASON_LIMIT_PRICE_NON_POSITIVE;
            }
        } else if ("MARKET".equals(orderType)) {
            if (price != null) {
                return REASON_MARKET_PRICE_NOT_ALLOWED;
            }
            if (requireMarketPrice && effectivePrice == null) {
                return REASON_MARKET_PRICE_UNAVAILABLE;
            }
        }
        if ("BUY".equals(intent.getSide()) && effectivePrice != null) {
            BigDecimal cost = effectivePrice.multiply(intent.getQuantity());
            if (cashRemaining.compareTo(cost) < 0) {
                return REASON_INSUFFICIENT_CASH;
            }
        }
        if ("SELL".equals(intent.getSide())) {
            BigDecimal available = positionBySymbol.getOrDefault(intent.getSymbolCode(), BigDecimal.ZERO);
            if (available.compareTo(intent.getQuantity()) < 0) {
                return REASON_INSUFFICIENT_POSITION;
            }
        }
        return null;
    }

    private BigDecimal resolveEffectivePrice(TradeOrderIntentEntity intent, String executionMode) {
        if (intent.getPrice() != null) {
            return intent.getPrice();
        }
        if (!PaperOrderExecutor.EXECUTION_MODE_PAPER.equals(executionMode)) {
            return null;
        }
        return marketPriceItemRepository.findFirstBySymbolCodeOrderBySnapshotAtDesc(intent.getSymbolCode())
                .map(MarketPriceItemEntity::getLastPrice)
                .orElse(null);
    }
}
