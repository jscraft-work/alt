package work.jscraft.alt;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioEntity;
import work.jscraft.alt.portfolio.infrastructure.persistence.PortfolioRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.CycleExecutionOrchestrator;
import work.jscraft.alt.trading.application.ops.SetupMetricService;
import work.jscraft.alt.trading.application.ops.SetupMetricService.MetricSnapshot;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchEntity;
import work.jscraft.alt.trading.infrastructure.persistence.PaperTradeMatchRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 6 — paper 1 cycle full chain end-to-end IT + 운영자 시연 시나리오 검증.
 *
 * <p>Full path: cycle 시작 → LLM decision → PaperOrderExecutor (walker + 비용 적재 + portfolio + invariant
 * self-check) → SELL 시 PaperTradeMatcher (FIFO + multi-row) → paper_trade_match INSERT →
 * SetupMetricService.computeRecent → 4 지표 정확값.
 *
 * <p>운영자 plan v3-minimal §4 의 D+7 시연 쿼리도 그대로 실행해 column 정확성 검증:
 * <ul>
 *   <li>{@code SELECT exit_time, symbol_code, gross_pnl_pct, net_pnl_pct, slippage_*_pct, sell_tax_pct,
 *       fee_pct FROM paper_trade_match WHERE strategy_instance_id = ... ORDER BY exit_time DESC LIMIT 30}</li>
 *   <li>{@code SELECT side, requested_price, avg_filled_price, paper_requested_amount,
 *       paper_slippage_amount, paper_sell_tax_amount, paper_commission_amount, paper_actual_amount,
 *       paper_walk_levels, paper_partial_fill_ratio FROM trade_order WHERE strategy_instance_id = ... AND
 *       execution_mode = 'paper' ORDER BY filled_at DESC LIMIT 30}</li>
 * </ul>
 */
class PaperEvalEndToEndIT extends TradingCycleIntegrationTestSupport {

    @Autowired
    private CycleExecutionOrchestrator orchestrator;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @Autowired
    private PaperTradeMatchRepository paperTradeMatchRepository;

    @Autowired
    private SetupMetricService setupMetricService;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
        fakeTradingDecisionEngine.resetAll();
    }

    @Test
    void multiCycle_buyBuySell_endToEndChain_metricsAndDemoQueriesAreCorrect() throws Exception {
        StrategyInstanceEntity instance = createActiveInstance("KR 박스 v1", "paper");
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        addWatchlist(instance, samsung);
        seedPortfolio(instance, new BigDecimal("10000000.0000"));

        // ===== Cycle 1: BUY 3 주 80,000 =====
        seedOrderbook("005930",
                List.of(80_000L, 80_100L, 80_200L, 80_300L, 80_400L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(79_900L, 79_800L, 79_700L, 79_600L, 79_500L),
                List.of(100L, 100L, 100L, 100L, 100L));
        primeBuySingleOrder("005930", 3, 80_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // ===== Cycle 2: BUY 4 주 80,500 (10 분 후) =====
        mutableClock.setInstant(Instant.parse("2026-05-11T01:10:00Z"));
        seedOrderbook("005930",
                List.of(80_500L, 80_600L, 80_700L, 80_800L, 80_900L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(80_400L, 80_300L, 80_200L, 80_100L, 80_000L),
                List.of(100L, 100L, 100L, 100L, 100L));
        primeBuySingleOrder("005930", 4, 80_500);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // ===== Cycle 3: SELL 5 주 81,000 (20 분 후) =====
        mutableClock.setInstant(Instant.parse("2026-05-11T01:30:00Z"));
        seedOrderbook("005930",
                List.of(81_100L, 81_200L, 81_300L, 81_400L, 81_500L),
                List.of(100L, 100L, 100L, 100L, 100L),
                List.of(81_000L, 80_900L, 80_800L, 80_700L, 80_600L),
                List.of(100L, 100L, 100L, 100L, 100L));
        primeSellSingleOrder("005930", 5, 81_000);
        orchestrator.runOnce(instance.getId(), "test-worker");

        // ===== Verification 1: paper_trade_match FIFO multi-row =====
        List<PaperTradeMatchEntity> matches = paperTradeMatchRepository.findAll();
        assertThat(matches).hasSize(2);
        BigDecimal totalMatched = matches.stream()
                .map(PaperTradeMatchEntity::getMatchedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalMatched).isEqualByComparingTo("5");

        // ===== Verification 2: SetupMetricService 의 4 지표 + 비용 wall =====
        MetricSnapshot snapshot = setupMetricService.computeRecent(instance.getId(), 30);
        assertThat(snapshot.tradesCount()).isEqualTo(2);
        // 두 매칭 모두 net_pnl_pct 양수 (sell 81,000 vs buy 80,000 / 80,500 — cost wall 차감 후도 +)
        assertThat(snapshot.wins()).isEqualTo(2);
        assertThat(snapshot.losses()).isZero();
        assertThat(snapshot.hitRate()).isEqualByComparingTo("1.0000");
        assertThat(snapshot.ev()).isGreaterThan(BigDecimal.ZERO);
        // losses 0 → pf undefined
        assertThat(snapshot.pf()).isNull();
        // 비용 wall 실측 — slip + tax + fee > 0
        assertThat(snapshot.avgCostTotalPct()).isGreaterThan(BigDecimal.ZERO);

        // ===== Verification 3: 운영자 시연 SQL — paper_trade_match 직접 쿼리 =====
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT exit_time, symbol_code, gross_pnl_pct, net_pnl_pct, "
                            + "slippage_buy_pct, slippage_sell_pct, sell_tax_pct, fee_pct "
                            + "FROM paper_trade_match "
                            + "WHERE strategy_instance_id = '" + instance.getId() + "' "
                            + "ORDER BY exit_time DESC LIMIT 30");
            int rowCount = 0;
            while (rs.next()) {
                assertThat(rs.getString("symbol_code")).isEqualTo("005930");
                assertThat(rs.getBigDecimal("gross_pnl_pct")).isNotNull();
                assertThat(rs.getBigDecimal("net_pnl_pct")).isNotNull();
                assertThat(rs.getBigDecimal("sell_tax_pct")).isGreaterThan(BigDecimal.ZERO);
                assertThat(rs.getBigDecimal("fee_pct")).isGreaterThan(BigDecimal.ZERO);
                rowCount++;
            }
            assertThat(rowCount).isEqualTo(2);
        }

        // ===== Verification 4: 운영자 시연 SQL — trade_order 직접 쿼리 (paper_* 9 컬럼) =====
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT toi.side, t_o.requested_price, t_o.avg_filled_price, "
                            + "t_o.paper_requested_amount, t_o.paper_slippage_amount, "
                            + "t_o.paper_sell_tax_amount, t_o.paper_commission_amount, t_o.paper_actual_amount, "
                            + "t_o.paper_walk_levels, t_o.paper_partial_fill_ratio "
                            + "FROM trade_order t_o "
                            + "JOIN trade_order_intent toi ON t_o.trade_order_intent_id = toi.id "
                            + "WHERE t_o.strategy_instance_id = '" + instance.getId() + "' "
                            + "  AND t_o.execution_mode = 'paper' "
                            + "ORDER BY t_o.filled_at DESC LIMIT 30");
            int orderRows = 0;
            while (rs.next()) {
                String side = rs.getString("side");
                assertThat(side).isIn("BUY", "SELL");
                assertThat(rs.getBigDecimal("paper_actual_amount")).isGreaterThan(BigDecimal.ZERO);
                assertThat(rs.getBigDecimal("paper_requested_amount")).isGreaterThan(BigDecimal.ZERO);
                assertThat(rs.getBigDecimal("paper_commission_amount")).isGreaterThan(BigDecimal.ZERO);
                if ("SELL".equals(side)) {
                    assertThat(rs.getBigDecimal("paper_sell_tax_amount")).isGreaterThan(BigDecimal.ZERO);
                } else {
                    assertThat(rs.getBigDecimal("paper_sell_tax_amount")).isEqualByComparingTo("0");
                }
                assertThat(rs.getShort("paper_walk_levels")).isGreaterThanOrEqualTo((short) 1);
                orderRows++;
            }
            // 3 cycles = 3 trade_order (BUY × 2 + SELL × 1)
            assertThat(orderRows).isEqualTo(3);
        }

        // ===== Verification 5: invariant 위반 0 회 — SQL 직접 검증 (lazy 회피)
        // BUY:  actual = req + slip + comm
        // SELL: actual = req + slip - tax - comm
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT toi.side, t_o.paper_requested_amount, t_o.paper_slippage_amount, "
                            + "t_o.paper_sell_tax_amount, t_o.paper_commission_amount, t_o.paper_actual_amount "
                            + "FROM trade_order t_o "
                            + "JOIN trade_order_intent toi ON t_o.trade_order_intent_id = toi.id "
                            + "WHERE t_o.strategy_instance_id = '" + instance.getId() + "' "
                            + "  AND t_o.execution_mode = 'paper'");
            int invariantChecked = 0;
            while (rs.next()) {
                String side = rs.getString("side");
                BigDecimal req = rs.getBigDecimal("paper_requested_amount");
                BigDecimal slip = rs.getBigDecimal("paper_slippage_amount");
                BigDecimal tax = rs.getBigDecimal("paper_sell_tax_amount");
                BigDecimal comm = rs.getBigDecimal("paper_commission_amount");
                BigDecimal actual = rs.getBigDecimal("paper_actual_amount");

                BigDecimal expected = "BUY".equals(side)
                        ? req.add(slip).add(comm)
                        : req.add(slip).subtract(tax).subtract(comm);
                assertThat(expected).as("invariant 식 정합 — side=%s", side).isEqualByComparingTo(actual);
                invariantChecked++;
            }
            assertThat(invariantChecked).isEqualTo(3);  // BUY × 2 + SELL × 1
        }
    }

    // ===== helpers =====

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

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID toUuid() {
        return UUID.randomUUID();
    }
}
