package work.jscraft.alt.portfolio.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;

@Service
public class PortfolioUpdateService {

    private static final MathContext AVG_CONTEXT = new MathContext(20);
    private static final int CASH_SCALE = 4;
    private static final int PRICE_SCALE = 8;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final ObjectMapper objectMapper;

    public PortfolioUpdateService(
            PortfolioRepository portfolioRepository,
            PortfolioPositionRepository portfolioPositionRepository,
            ObjectMapper objectMapper) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PortfolioSnapshot applyBuy(
            UUID strategyInstanceId,
            String symbolCode,
            BigDecimal quantity,
            BigDecimal fillPrice) {
        PortfolioEntity portfolio = requirePortfolio(strategyInstanceId);
        BigDecimal cost = fillPrice.multiply(quantity);
        portfolio.setCashAmount(portfolio.getCashAmount().subtract(cost).setScale(CASH_SCALE, RoundingMode.HALF_UP));

        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(strategyInstanceId, symbolCode)
                .orElseGet(() -> {
                    PortfolioPositionEntity entity = new PortfolioPositionEntity();
                    entity.setStrategyInstanceId(strategyInstanceId);
                    entity.setSymbolCode(symbolCode);
                    entity.setQuantity(BigDecimal.ZERO);
                    entity.setAvgBuyPrice(BigDecimal.ZERO);
                    return entity;
                });

        BigDecimal previousQty = position.getQuantity();
        BigDecimal newQty = previousQty.add(quantity);
        BigDecimal newAvg;
        if (newQty.signum() == 0) {
            newAvg = BigDecimal.ZERO;
        } else {
            BigDecimal previousCost = previousQty.multiply(position.getAvgBuyPrice());
            newAvg = previousCost.add(cost).divide(newQty, AVG_CONTEXT);
        }
        position.setQuantity(newQty.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        position.setAvgBuyPrice(newAvg.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        portfolioPositionRepository.saveAndFlush(position);

        recomputeTotalAsset(portfolio);
        portfolioRepository.saveAndFlush(portfolio);
        return buildSnapshot(strategyInstanceId, portfolio);
    }

    @Transactional
    public PortfolioSnapshot applySell(
            UUID strategyInstanceId,
            String symbolCode,
            BigDecimal quantity,
            BigDecimal fillPrice) {
        PortfolioEntity portfolio = requirePortfolio(strategyInstanceId);
        BigDecimal proceeds = fillPrice.multiply(quantity);
        portfolio.setCashAmount(portfolio.getCashAmount().add(proceeds).setScale(CASH_SCALE, RoundingMode.HALF_UP));

        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(strategyInstanceId, symbolCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "보유 종목 없이 매도 시도: " + symbolCode));

        BigDecimal realized = fillPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);
        portfolio.setRealizedPnlToday(
                portfolio.getRealizedPnlToday().add(realized).setScale(CASH_SCALE, RoundingMode.HALF_UP));

        BigDecimal newQty = position.getQuantity().subtract(quantity);
        position.setQuantity(newQty.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        portfolioPositionRepository.saveAndFlush(position);

        recomputeTotalAsset(portfolio);
        portfolioRepository.saveAndFlush(portfolio);
        return buildSnapshot(strategyInstanceId, portfolio);
    }

    /**
     * BUY paper 체결 — {@code cashOut} 만큼 portfolio.cash 차감, position 추가.
     *
     * <p>paper 의 경우 slippage + 위탁수수료 포함 amount 를 {@code cashOut} 으로 받아 정확히 차감 →
     * V17 의 paper_actual_amount invariant 와 일치.
     *
     * <p>평단가 (avg_buy_price) 는 {@code fillPrice} (한 틱 양보 포함 평균 체결가) 기준으로 가중평균.
     * 위탁수수료는 자본 손실 영역으로 분류하고 평단가에는 반영하지 않는다.
     */
    @Transactional
    public PortfolioSnapshot applyBuyWithCost(
            UUID strategyInstanceId,
            String symbolCode,
            BigDecimal quantity,
            BigDecimal fillPrice,
            BigDecimal cashOut) {
        PortfolioEntity portfolio = requirePortfolio(strategyInstanceId);
        portfolio.setCashAmount(portfolio.getCashAmount().subtract(cashOut)
                .setScale(CASH_SCALE, RoundingMode.HALF_UP));

        BigDecimal fillCost = fillPrice.multiply(quantity);
        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(strategyInstanceId, symbolCode)
                .orElseGet(() -> {
                    PortfolioPositionEntity entity = new PortfolioPositionEntity();
                    entity.setStrategyInstanceId(strategyInstanceId);
                    entity.setSymbolCode(symbolCode);
                    entity.setQuantity(BigDecimal.ZERO);
                    entity.setAvgBuyPrice(BigDecimal.ZERO);
                    return entity;
                });

        BigDecimal previousQty = position.getQuantity();
        BigDecimal newQty = previousQty.add(quantity);
        BigDecimal newAvg;
        if (newQty.signum() == 0) {
            newAvg = BigDecimal.ZERO;
        } else {
            BigDecimal previousCost = previousQty.multiply(position.getAvgBuyPrice());
            newAvg = previousCost.add(fillCost).divide(newQty, AVG_CONTEXT);
        }
        position.setQuantity(newQty.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        position.setAvgBuyPrice(newAvg.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        portfolioPositionRepository.saveAndFlush(position);

        recomputeTotalAsset(portfolio);
        portfolioRepository.saveAndFlush(portfolio);
        return buildSnapshot(strategyInstanceId, portfolio);
    }

    /**
     * SELL paper 체결 — {@code cashIn} 만큼 portfolio.cash 증가, position 차감, realized PnL 누적.
     *
     * <p>{@code cashIn} = grossFill - 매도세 - 위탁수수료 (V17 paper_actual_amount).
     * realized PnL 은 {@code cashIn} (cost 차감 후 순수익) 과 평단가 기준 cost 의 차로 계산 — 운영자가 매매
     * 결과 row 한 줄로 net PnL 직관 가능하게.
     */
    @Transactional
    public PortfolioSnapshot applySellWithCost(
            UUID strategyInstanceId,
            String symbolCode,
            BigDecimal quantity,
            BigDecimal fillPrice,
            BigDecimal cashIn) {
        PortfolioEntity portfolio = requirePortfolio(strategyInstanceId);
        portfolio.setCashAmount(portfolio.getCashAmount().add(cashIn)
                .setScale(CASH_SCALE, RoundingMode.HALF_UP));

        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(strategyInstanceId, symbolCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "보유 종목 없이 매도 시도: " + symbolCode));

        // realized PnL 은 cashIn (cost 차감 후 net) 와 평단가 cost 차이 — net 기준
        BigDecimal costBasis = position.getAvgBuyPrice().multiply(quantity);
        BigDecimal realized = cashIn.subtract(costBasis);
        portfolio.setRealizedPnlToday(
                portfolio.getRealizedPnlToday().add(realized).setScale(CASH_SCALE, RoundingMode.HALF_UP));

        BigDecimal newQty = position.getQuantity().subtract(quantity);
        position.setQuantity(newQty.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        portfolioPositionRepository.saveAndFlush(position);

        recomputeTotalAsset(portfolio);
        portfolioRepository.saveAndFlush(portfolio);
        return buildSnapshot(strategyInstanceId, portfolio);
    }

    public PortfolioSnapshot snapshot(UUID strategyInstanceId) {
        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(strategyInstanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "portfolio가 없습니다: " + strategyInstanceId));
        return buildSnapshot(strategyInstanceId, portfolio);
    }

    private void recomputeTotalAsset(PortfolioEntity portfolio) {
        List<PortfolioPositionEntity> positions =
                portfolioPositionRepository.findByStrategyInstanceId(portfolio.getStrategyInstanceId());
        BigDecimal positionsValue = BigDecimal.ZERO;
        for (PortfolioPositionEntity position : positions) {
            positionsValue = positionsValue.add(position.getQuantity().multiply(position.getAvgBuyPrice()));
        }
        portfolio.setTotalAssetAmount(
                portfolio.getCashAmount().add(positionsValue).setScale(CASH_SCALE, RoundingMode.HALF_UP));
    }

    private PortfolioEntity requirePortfolio(UUID strategyInstanceId) {
        return portfolioRepository.findByStrategyInstanceId(strategyInstanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "portfolio가 없습니다: " + strategyInstanceId));
    }

    private PortfolioSnapshot buildSnapshot(UUID strategyInstanceId, PortfolioEntity portfolio) {
        List<PortfolioPositionEntity> positions =
                portfolioPositionRepository.findByStrategyInstanceId(strategyInstanceId);
        return new PortfolioSnapshot(
                portfolio.getCashAmount(),
                portfolio.getTotalAssetAmount(),
                portfolio.getRealizedPnlToday(),
                positions);
    }

    public ObjectNode toJson(PortfolioSnapshot snapshot) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("cashAmount", snapshot.cashAmount());
        node.put("totalAssetAmount", snapshot.totalAssetAmount());
        node.put("realizedPnlToday", snapshot.realizedPnlToday());
        ArrayNode positionsArray = objectMapper.createArrayNode();
        for (PortfolioPositionEntity position : snapshot.positions()) {
            ObjectNode pos = objectMapper.createObjectNode();
            pos.put("symbolCode", position.getSymbolCode());
            pos.put("quantity", position.getQuantity());
            pos.put("avgBuyPrice", position.getAvgBuyPrice());
            positionsArray.add(pos);
        }
        node.set("positions", positionsArray);
        return node;
    }

    public record PortfolioSnapshot(
            BigDecimal cashAmount,
            BigDecimal totalAssetAmount,
            BigDecimal realizedPnlToday,
            List<PortfolioPositionEntity> positions) {
    }
}
