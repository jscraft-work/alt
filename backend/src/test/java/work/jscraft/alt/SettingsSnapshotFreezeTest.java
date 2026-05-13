package work.jscraft.alt;

import java.time.Instant;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.marketdata.infrastructure.persistence.AssetMasterEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshot;
import work.jscraft.alt.trading.application.cycle.SettingsSnapshotProvider;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsSnapshotFreezeTest extends TradingCycleIntegrationTestSupport {

    @Autowired
    private SettingsSnapshotProvider snapshotProvider;

    @Autowired
    private StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        mutableClock.setInstant(Instant.parse("2026-05-11T01:00:00Z"));
    }

    @Test
    void firstSnapshotIsNotAffectedByLaterSettingChanges() {
        StrategyInstanceEntity instance = createActiveInstance("KR 모멘텀 A", "paper");
        StrategyInstancePromptVersionEntity v1 = instance.getCurrentPromptVersion();
        AssetMasterEntity samsung = createAsset("005930", "삼성전자", null);
        linkWatchlist(instance, samsung);

        SettingsSnapshot first = snapshotProvider.capture(instance.getId());

        assertThat(first.promptVersionId()).isEqualTo(v1.getId());
        assertThat(first.promptText()).isEqualTo(DEFAULT_CYCLE_PROMPT);
        assertThat(first.watchlistSymbols()).containsExactly("005930");

        // 사이클 시작 후 인스턴스 설정 변경
        String v2PromptText = DEFAULT_CYCLE_PROMPT + "\n<!-- v2 tweak -->";
        StrategyInstancePromptVersionEntity v2 = addPromptVersion(instance, 2, v2PromptText, "tweak");
        instance.setCurrentPromptVersion(v2);
        ObjectNode newConfig = objectMapper.createObjectNode();
        newConfig.put("slippageBps", 99);
        instance.setExecutionConfigOverrideJson(newConfig);
        strategyInstanceRepository.saveAndFlush(instance);

        AssetMasterEntity sk = createAsset("000660", "SK하이닉스", null);
        linkWatchlist(instance, sk);

        // 첫 스냅샷은 그대로 유지되어야 한다 (record는 immutable)
        assertThat(first.promptVersionId()).isEqualTo(v1.getId());
        assertThat(first.promptText()).isEqualTo(DEFAULT_CYCLE_PROMPT);
        assertThat(first.watchlistSymbols()).containsExactly("005930");
        if (first.executionConfig() != null && first.executionConfig().has("slippageBps")) {
            assertThat(first.executionConfig().path("slippageBps").asInt()).isEqualTo(5);
        }

        // 두 번째 스냅샷은 변경된 값을 반영
        SettingsSnapshot second = snapshotProvider.capture(instance.getId());
        assertThat(second.promptVersionId()).isEqualTo(v2.getId());
        assertThat(second.promptText()).isEqualTo(v2PromptText);
        assertThat(second.watchlistSymbols()).containsExactlyInAnyOrder("005930", "000660");
        assertThat(second.executionConfig().path("slippageBps").asInt()).isEqualTo(99);
    }

    private void linkWatchlist(StrategyInstanceEntity instance, AssetMasterEntity asset) {
        StrategyInstanceWatchlistRelationEntity relation = new StrategyInstanceWatchlistRelationEntity();
        relation.setStrategyInstance(instance);
        relation.setAssetMaster(asset);
        watchlistRelationRepository.saveAndFlush(relation);
    }
}
