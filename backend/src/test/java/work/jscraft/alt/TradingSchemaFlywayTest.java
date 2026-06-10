package work.jscraft.alt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(PostgreSqlTestConfiguration.class)
class TradingSchemaFlywayTest {

    @Autowired
    private PostgreSQLContainer<?> postgreSqlContainer;

    @Test
    void flywayAppliesCumulativeTradingSchema() throws Exception {
        String databaseName = "trading_verify_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(databaseName);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(
                            jdbcUrl(databaseName),
                            postgreSqlContainer.getUsername(),
                            postgreSqlContainer.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(19);

            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl(databaseName),
                    postgreSqlContainer.getUsername(),
                    postgreSqlContainer.getPassword());
                 Statement statement = connection.createStatement()) {

                assertThat(singleInt(statement, "select count(*) from flyway_schema_history where success = true"))
                        .isEqualTo(19);
                assertThat(regclass(statement, "strategy_instance")).isEqualTo("strategy_instance");
                assertThat(regclass(statement, "strategy_instance_prompt_version"))
                        .isEqualTo("strategy_instance_prompt_version");
                assertThat(regclass(statement, "strategy_instance_watchlist_relation"))
                        .isEqualTo("strategy_instance_watchlist_relation");
                assertThat(regclass(statement, "portfolio")).isEqualTo("portfolio");
                assertThat(regclass(statement, "portfolio_position")).isEqualTo("portfolio_position");
                assertThat(regclass(statement, "trade_cycle_log")).isEqualTo("trade_cycle_log");
                assertThat(regclass(statement, "trade_decision_log")).isEqualTo("trade_decision_log");
                assertThat(regclass(statement, "trade_order_intent")).isEqualTo("trade_order_intent");
                assertThat(regclass(statement, "trade_order")).isEqualTo("trade_order");
                assertThat(regclass(statement, "audit_log")).isEqualTo("audit_log");
                assertThat(regclass(statement, "ops_event")).isEqualTo("ops_event");
                assertThat(regclass(statement, "market_price_item")).isEqualTo("market_price_item");
                assertThat(regclass(statement, "market_minute_item")).isEqualTo("market_minute_item");
                assertThat(regclass(statement, "market_daily_item")).isEqualTo("market_daily_item");
                assertThat(regclass(statement, "news_item")).isEqualTo("news_item");
                assertThat(regclass(statement, "news_asset_relation")).isEqualTo("news_asset_relation");
                assertThat(regclass(statement, "disclosure_item")).isEqualTo("disclosure_item");
                assertThat(regclass(statement, "macro_item")).isEqualTo("macro_item");
                assertThat(regclass(statement, "scheduled_tasks")).isEqualTo("scheduled_tasks");
                assertThat(columnExists(statement, "portfolio_position", "last_mark_price")).isFalse();
                assertThat(columnExists(statement, "portfolio_position", "unrealized_pnl")).isFalse();

                // V17: trade_order paper 비용 breakdown 9 컬럼
                assertThat(columnExists(statement, "trade_order", "paper_requested_amount")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_slippage_amount")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_sell_tax_amount")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_commission_amount")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_actual_amount")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_walk_levels")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_partial_fill_ratio")).isTrue();
                assertThat(columnExists(statement, "trade_order", "paper_orderbook_snapshot_json")).isTrue();
                assertThat(columnExists(statement, "trade_order", "unfilled_quantity")).isTrue();

                // V18: paper_trade_match 신규 테이블
                assertThat(regclass(statement, "paper_trade_match")).isEqualTo("paper_trade_match");
                assertThat(columnExists(statement, "paper_trade_match", "buy_trade_order_id")).isTrue();
                assertThat(columnExists(statement, "paper_trade_match", "sell_trade_order_id")).isTrue();
                assertThat(columnExists(statement, "paper_trade_match", "matched_quantity")).isTrue();
                assertThat(columnExists(statement, "paper_trade_match", "gross_pnl_pct")).isTrue();
                assertThat(columnExists(statement, "paper_trade_match", "net_pnl_pct")).isTrue();

                // V19: market_investor_flow_item 신규 테이블 (개인/외국인/기관 일별 순매수)
                assertThat(regclass(statement, "market_investor_flow_item"))
                        .isEqualTo("market_investor_flow_item");
                assertThat(columnExists(statement, "market_investor_flow_item", "trade_date")).isTrue();
                assertThat(columnExists(statement, "market_investor_flow_item", "indv_ntby_qty")).isTrue();
                assertThat(columnExists(statement, "market_investor_flow_item", "frgn_ntby_qty")).isTrue();
                assertThat(columnExists(statement, "market_investor_flow_item", "orgn_ntby_qty")).isTrue();
                assertThat(columnExists(statement, "market_investor_flow_item", "orgn_ntby_amt")).isTrue();
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    private void createDatabase(String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl("postgres"),
                postgreSqlContainer.getUsername(),
                postgreSqlContainer.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + databaseName);
        }
    }

    private void dropDatabase(String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl("postgres"),
                postgreSqlContainer.getUsername(),
                postgreSqlContainer.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("drop database if exists " + databaseName);
        }
    }

    private String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgreSqlContainer.getHost(),
                postgreSqlContainer.getFirstMappedPort(),
                databaseName);
    }

    private int singleInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String regclass(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("select to_regclass('%s')".formatted(tableName))) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private boolean columnExists(Statement statement, String tableName, String columnName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                select exists (
                    select 1
                    from information_schema.columns
                    where table_name = '%s' and column_name = '%s'
                )
                """.formatted(tableName, columnName))) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
