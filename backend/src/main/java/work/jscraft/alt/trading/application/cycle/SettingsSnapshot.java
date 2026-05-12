package work.jscraft.alt.trading.application.cycle;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record SettingsSnapshot(
        UUID strategyInstanceId,
        String executionMode,
        UUID promptVersionId,
        String promptText,
        UUID tradingModelProfileId,
        JsonNode inputSpec,
        JsonNode executionConfig,
        List<String> watchlistSymbols,
        OffsetDateTime capturedAt) {
}
