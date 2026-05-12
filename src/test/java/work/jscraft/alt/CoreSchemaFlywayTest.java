package work.jscraft.alt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(PostgreSqlTestConfiguration.class)
class CoreSchemaFlywayTest {

    @Autowired
    private PostgreSQLContainer<?> postgreSqlContainer;

    @Test
    void flywayAppliesCoreSchemaToEmptyDatabase() throws Exception {
        String databaseName = "flyway_verify_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(databaseName);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(
                            jdbcUrl(databaseName),
                            postgreSqlContainer.getUsername(),
                            postgreSqlContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("1"))
                    .load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl(databaseName),
                    postgreSqlContainer.getUsername(),
                    postgreSqlContainer.getPassword());
                 Statement statement = connection.createStatement()) {

                assertThat(singleInt(statement, "select count(*) from flyway_schema_history where success = true"))
                        .isEqualTo(1);
                assertThat(regclass(statement, "llm_model_profile")).isEqualTo("llm_model_profile");
                assertThat(regclass(statement, "strategy_template")).isEqualTo("strategy_template");
                assertThat(regclass(statement, "system_parameter")).isEqualTo("system_parameter");
                assertThat(regclass(statement, "broker_account")).isEqualTo("broker_account");
                assertThat(regclass(statement, "asset_master")).isEqualTo("asset_master");
                assertThat(regclass(statement, "app_user")).isEqualTo("app_user");
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
}
