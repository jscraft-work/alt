package work.jscraft.alt.trading.application.cycle;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstancePromptVersionEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceRepository;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationEntity;
import work.jscraft.alt.strategy.infrastructure.persistence.StrategyInstanceWatchlistRelationRepository;

@Service
@Transactional(readOnly = true)
public class SettingsSnapshotProvider {

    private final StrategyInstanceRepository strategyInstanceRepository;
    private final StrategyInstanceWatchlistRelationRepository watchlistRelationRepository;
    private final Clock clock;

    public SettingsSnapshotProvider(
            StrategyInstanceRepository strategyInstanceRepository,
            StrategyInstanceWatchlistRelationRepository watchlistRelationRepository,
            Clock clock) {
        this.strategyInstanceRepository = strategyInstanceRepository;
        this.watchlistRelationRepository = watchlistRelationRepository;
        this.clock = clock;
    }

    public SettingsSnapshot capture(UUID strategyInstanceId) {
        StrategyInstanceEntity instance = strategyInstanceRepository.findById(strategyInstanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "전략 인스턴스를 찾을 수 없습니다."));

        StrategyInstancePromptVersionEntity promptVersion = instance.getCurrentPromptVersion();
        if (promptVersion == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "현재 프롬프트 버전이 설정되지 않아 사이클을 시작할 수 없습니다.");
        }

        JsonNode executionConfig = instance.getExecutionConfigOverrideJson() != null
                ? instance.getExecutionConfigOverrideJson().deepCopy()
                : instance.getStrategyTemplate().getDefaultExecutionConfigJson().deepCopy();

        UUID tradingModelProfileId = instance.getTradingModelProfile() != null
                ? instance.getTradingModelProfile().getId()
                : instance.getStrategyTemplate().getDefaultTradingModelProfile().getId();

        List<String> watchlistSymbols = new ArrayList<>();
        for (StrategyInstanceWatchlistRelationEntity relation
                : watchlistRelationRepository.findByStrategyInstanceIdOrderByCreatedAtAsc(strategyInstanceId)) {
            watchlistSymbols.add(relation.getAssetMaster().getSymbolCode());
        }

        return new SettingsSnapshot(
                instance.getId(),
                instance.getExecutionMode(),
                promptVersion.getId(),
                promptVersion.getPromptText(),
                tradingModelProfileId,
                executionConfig,
                List.copyOf(watchlistSymbols),
                OffsetDateTime.now(clock));
    }
}
