package work.jscraft.alt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchEntity;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchRepository;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderEntity;
import work.jscraft.alt.trading.infrastructure.persistence.TradeOrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V18 paper_trade_match FIFO 매칭 IT — BUY cycle + SELL cycle 패턴으로 PaperTradeMatcher 의 매칭/INSERT 검증.
 *
 * <p>4 시나리오 중 핵심 2:
 * <ul>
 *   <li>(a) 1 BUY + 1 SELL 완전 매칭 — paper_trade_match 1 row</li>
 *   <li>(b) 2 BUY + 1 SELL 부분 매칭 — paper_trade_match 2 row (multi-row, FIFO)</li>
 * </ul>
 * (c) 부분 SELL + (d) 미매칭 BUY 없음은 ops 보조 시나리오 — 우선 (a)/(b) 가 핵심 로직 커버.
 */
class PaperTradeMatcherIT extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private PaperTradeMatchRepository paperTradeMatchRepository;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void singleBuySingleSell_oneFullMatch() {
        StrategyInstanceEntity instance = createActiveInstance("KR 매칭 A", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("5000000.0000"));
        // BUY cycle 1: ask 1 = 80,000, BUY 5 주 LIMIT 80,000
        seedOrderbook("005930",
                java.util.List.of(80_000L, 80_100L, 80_200L, 80_300L, 80_400L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(79_900L, 79_800L, 79_700L, 79_600L, 79_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));

        primeBuySingleOrder("005930", 5, 80_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // 30 분 후, SELL cycle: 호가 상승 (bid 1 = 81,000), SELL 5 주 LIMIT 81,000
        mutableClock.setInstant(Instant.parse("2026-05-11T01:30:00Z"));
        seedOrderbook("005930",
                java.util.List.of(81_100L, 81_200L, 81_300L, 81_400L, 81_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(81_000L, 80_900L, 80_800L, 80_700L, 80_600L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));

        primeSellSingleOrder("005930", 5, 81_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository.findAll();
        assertThat(matches).hasSize(1);
        PaperTradeMatchEntity match = matches.get(0);
        assertThat(match.getSymbolCode()).isEqualTo("005930");
        assertThat(match.getMatchedQuantity()).isEqualByComparingTo("5");
        assertThat(match.getHoldingMinutes()).isEqualTo(30);
        // gross = (81_000 - 80_000) / 80_000 = 0.0125
        assertThat(match.getGrossPnlPct()).isEqualByComparingTo("0.012500");
        // net = (sell_actual - buy_actual) / buy_actual — sell 이 더 비싸졌으나 비용 차감 후 양수 (paper sim cost wall 작음)
        assertThat(match.getNetPnlPct()).isPositive();
        // BUY 쪽 비용 metric
        assertThat(match.getSlippageBuyPct()).isNotNull();
        assertThat(match.getSlippageSellPct()).isNotNull();
        assertThat(match.getSellTaxPct()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(match.getFeePct()).isNotNull().isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void twoBuysOneSell_multiRowMatch() {
        StrategyInstanceEntity instance = createActiveInstance("KR 매칭 B", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("10000000.0000"));

        // BUY cycle 1: 3 주 80,000
        seedOrderbook("005930",
                java.util.List.of(80_000L, 80_100L, 80_200L, 80_300L, 80_400L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(79_900L, 79_800L, 79_700L, 79_600L, 79_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeBuySingleOrder("005930", 3, 80_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // 10 분 후 BUY cycle 2: 4 주 80,500
        mutableClock.setInstant(Instant.parse("2026-05-11T01:10:00Z"));
        seedOrderbook("005930",
                java.util.List.of(80_500L, 80_600L, 80_700L, 80_800L, 80_900L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(80_400L, 80_300L, 80_200L, 80_100L, 80_000L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeBuySingleOrder("005930", 4, 80_500);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // 20 분 후 SELL cycle: 5 주 (첫 BUY 3 + 두번째 BUY 2 = 5) 매도
        mutableClock.setInstant(Instant.parse("2026-05-11T01:30:00Z"));
        seedOrderbook("005930",
                java.util.List.of(81_100L, 81_200L, 81_300L, 81_400L, 81_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(81_000L, 80_900L, 80_800L, 80_700L, 80_600L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeSellSingleOrder("005930", 5, 81_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository.findAll();
        // FIFO: 첫 BUY 3 전체 + 두번째 BUY 2 = 2 rows
        assertThat(matches).hasSize(2);
        // entry_time ASC 순서로 정렬되지 않을 수 있어 matched_quantity 로 검증
        BigDecimal totalMatched = matches.stream()
                .map(PaperTradeMatchEntity::getMatchedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalMatched).isEqualByComparingTo("5");

        // 첫 BUY (80,000) 매칭 = 3 주, 두번째 BUY (80,500) 매칭 = 2 주
        assertThat(matches.stream().anyMatch(m -> m.getMatchedQuantity().compareTo(new BigDecimal("3")) == 0)).isTrue();
        assertThat(matches.stream().anyMatch(m -> m.getMatchedQuantity().compareTo(new BigDecimal("2")) == 0)).isTrue();
    }

    /**
     * (c) 1 BUY 후 1 부분 SELL → BUY 잔량 남음. paper_trade_match 1 row, matched_quantity &lt; buy.filled_quantity.
     */
    @Test
    void singleBuyPartialSell_oneRowWithBuyRemaining() {
        StrategyInstanceEntity instance = createActiveInstance("KR 매칭 C", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("10000000.0000"));

        seedOrderbook("005930",
                java.util.List.of(80_000L, 80_100L, 80_200L, 80_300L, 80_400L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(79_900L, 79_800L, 79_700L, 79_600L, 79_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeBuySingleOrder("005930", 5, 80_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        mutableClock.setInstant(Instant.parse("2026-05-11T01:30:00Z"));
        seedOrderbook("005930",
                java.util.List.of(81_100L, 81_200L, 81_300L, 81_400L, 81_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(81_000L, 80_900L, 80_800L, 80_700L, 80_600L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeSellSingleOrder("005930", 3, 81_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository.findAll();
        assertThat(matches).hasSize(1);
        // BUY 5 주 중 3 주만 매칭 — 잔량 2 주 남음
        assertThat(matches.get(0).getMatchedQuantity()).isEqualByComparingTo("3");
    }

    /**
     * 회귀: V17 이전 레거시 매수 주문은 {@code paper_actual_amount} 가 null 이다. SELL 매칭 시
     * 그 값을 곱하다 NPE 가 나면 SELL 트랜잭션이 통째로 롤백되어 "팔자 판단은 나오는데 안 팔리는" 증상이 된다.
     * 폴백(체결가×수량) 으로 NPE 없이 매칭·체결이 완료돼야 한다.
     */
    @Test
    void sellMatchesLegacyBuyWithNullActualAmount_completesWithoutNpe() {
        StrategyInstanceEntity instance = createActiveInstance("KR 레거시", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("5000000.0000"));

        seedOrderbook("005930",
                java.util.List.of(80_000L, 80_100L, 80_200L, 80_300L, 80_400L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(79_900L, 79_800L, 79_700L, 79_600L, 79_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeBuySingleOrder("005930", 5, 80_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // 레거시 주문 시뮬레이션: 매수 주문의 paper_actual_amount 를 null 로 만든다.
        List<TradeOrderEntity> buys = tradeOrderRepository.findAll();
        assertThat(buys).hasSize(1);
        TradeOrderEntity legacyBuy = buys.get(0);
        legacyBuy.setPaperActualAmount(null);
        tradeOrderRepository.saveAndFlush(legacyBuy);

        // 30 분 후 SELL — 매칭이 null 매수와 일어나지만 NPE 없이 완료돼야 한다.
        mutableClock.setInstant(Instant.parse("2026-05-11T01:30:00Z"));
        seedOrderbook("005930",
                java.util.List.of(81_100L, 81_200L, 81_300L, 81_400L, 81_500L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L),
                java.util.List.of(81_000L, 80_900L, 80_800L, 80_700L, 80_600L),
                java.util.List.of(100L, 100L, 100L, 100L, 100L));
        primeSellSingleOrder("005930", 5, 81_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // SELL 주문이 실제로 persist (롤백 안 됨) — buy + sell = 2 건
        assertThat(tradeOrderRepository.findAll()).hasSize(2);
        // 매칭 row 생성, net_pnl_pct 채워짐(NOT NULL 컬럼)
        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository.findAll();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedQuantity()).isEqualByComparingTo("5");
        assertThat(matches.get(0).getNetPnlPct()).isNotNull();
        assertThat(matches.get(0).getGrossPnlPct()).isEqualByComparingTo("0.012500");
    }

    private void primeBuySingleOrder(String symbolCode, int quantity, int price) {
        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"매수",
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"%s","side":"BUY","quantity":%d,"orderType":"LIMIT","price":%d,"rationale":"근거"}
                  ]
                }
                """.formatted(symbolCode, quantity, price));
    }

    private void primeSellSingleOrder(String symbolCode, int quantity, int price) {
        fakeTradingDecisionEngine.primeSuccess("""
                {
                  "cycleStatus":"EXECUTE",
                  "summary":"매도",
                  "orders":[
                    {"sequenceNo":1,"symbolCode":"%s","side":"SELL","quantity":%d,"orderType":"LIMIT","price":%d,"rationale":"근거"}
                  ]
                }
                """.formatted(symbolCode, quantity, price));
    }

    private void seedPortfolio(StrategyInstanceEntity instance, BigDecimal cash) {
        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setStrategyInstanceId(instance.getId());
        portfolio.setCashAmount(cash);
        portfolio.setTotalAssetAmount(cash);
        portfolio.setRealizedPnlToday(BigDecimal.ZERO);
        portfolioRepository.saveAndFlush(portfolio);
    }

    private void addWatchlist(StrategyInstanceEntity instance, AssetMasterEntity asset) {
        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(instance);
        relation.setAssetMaster(asset);
        watchlistRelationRepository.saveAndFlush(relation);
    }
}
