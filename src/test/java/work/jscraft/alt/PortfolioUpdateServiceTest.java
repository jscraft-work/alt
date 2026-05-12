package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.portfolio.application.PortfolioUpdateService;
import work.jscraft.alt.portfolio.application.PortfolioUpdateService.PortfolioSnapshot;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioUpdateServiceTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private PortfolioUpdateService portfolioUpdateService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository portfolioPositionRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void buyThenAdditionalBuyComputesWeightedAvgBuyPrice() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        seedPortfolio(instance, new BigDecimal("1000000.0000"));

        portfolioUpdateService.applyBuy(instance.getId(), "005930",
                new BigDecimal("4"), new BigDecimal("80000"));
        portfolioUpdateService.applyBuy(instance.getId(), "005930",
                new BigDecimal("6"), new BigDecimal("85000"));

        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        // avg = (4*80000 + 6*85000) / 10 = (320000 + 510000) / 10 = 83000
        assertThat(position.getQuantity()).isEqualByComparingTo("10");
        assertThat(position.getAvgBuyPrice()).isEqualByComparingTo("83000");

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // cash = 1,000,000 - 4*80000 - 6*85000 = 1,000,000 - 320000 - 510000 = 170,000
        assertThat(portfolio.getCashAmount()).isEqualByComparingTo("170000.0000");
        // total = cash + qty * lastMark (=85000) = 170,000 + 10 * 85,000 = 1,020,000
        assertThat(portfolio.getTotalAssetAmount()).isEqualByComparingTo("1020000.0000");
    }

    @Test
    void sellAfterBuyAccumulatesRealizedPnl() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 B", "paper");
        seedPortfolio(instance, new BigDecimal("500000.0000"));

        portfolioUpdateService.applyBuy(instance.getId(), "005930",
                new BigDecimal("10"), new BigDecimal("80000"));
        portfolioUpdateService.applySell(instance.getId(), "005930",
                new BigDecimal("4"), new BigDecimal("85000"));

        PortfolioEntity portfolio = portfolioRepository.findByStrategyInstanceId(instance.getId()).orElseThrow();
        // realized = (85000 - 80000) * 4 = 20000
        assertThat(portfolio.getRealizedPnlToday()).isEqualByComparingTo("20000.0000");

        PortfolioPositionEntity position = portfolioPositionRepository
                .findByStrategyInstanceIdAndSymbolCode(instance.getId(), "005930").orElseThrow();
        assertThat(position.getQuantity()).isEqualByComparingTo("6");
        // avg unchanged after sell
        assertThat(position.getAvgBuyPrice()).isEqualByComparingTo("80000");
        // last mark = 85000
        assertThat(position.getLastMarkPrice()).isEqualByComparingTo("85000");
        // unrealized = (85000 - 80000) * 6 = 30000
        assertThat(position.getUnrealizedPnl()).isEqualByComparingTo("30000.0000");
    }

    @Test
    void snapshotReadsCurrentValues() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 C", "paper");
        seedPortfolio(instance, new BigDecimal("250000.0000"));
        portfolioUpdateService.applyBuy(instance.getId(), "005930",
                new BigDecimal("2"), new BigDecimal("80000"));

        PortfolioSnapshot snapshot = portfolioUpdateService.snapshot(instance.getId());
        assertThat(snapshot.cashAmount()).isEqualByComparingTo("90000.0000");
        assertThat(snapshot.positions()).hasSize(1);
        assertThat(snapshot.positions().get(0).getSymbolCode()).isEqualTo("005930");
    }

    private void seedPortfolio(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

}
