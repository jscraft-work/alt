package work.jscraft.alt.trading.application.decision;

import java.time.Duration;
import java.util.UUID;

public record LlmRequest(
        UUID sessionId,
        String engineName,
        String modelName,
        String promptText,
        Duration timeout) {
}
