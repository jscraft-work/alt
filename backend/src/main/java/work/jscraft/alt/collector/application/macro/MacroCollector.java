package work.jscraft.alt.collector.application.macro;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.macro.application.MacroGateway;
import work.jscraft.alt.macro.application.MacroGateway.MacroSnapshot;
import work.jscraft.alt.macro.application.MacroGatewayException;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemEntity;
import work.jscraft.alt.macro.infrastructure.persistence.MacroItemRepository;

@Component
public class MacroCollector {

    public static final String EVENT_TYPE = "macro.collect";

    private final MacroGateway macroGateway;
    private final MacroItemRepository macroItemRepository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public MacroCollector(
            MacroGateway macroGateway,
            MacroItemRepository macroItemRepository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.macroGateway = macroGateway;
        this.macroItemRepository = macroItemRepository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public CollectResult collect(LocalDate baseDate) {
        try {
            MacroSnapshot snapshot = macroGateway.fetchDailyMacro(baseDate);
            Optional<MacroItemEntity> existing = macroItemRepository.findByBaseDate(snapshot.baseDate());
            MacroItemEntity entity = existing.orElseGet(MacroItemEntity::new);
            entity.setBaseDate(snapshot.baseDate());
            entity.setPayloadJson(snapshot.payload());
            macroItemRepository.saveAndFlush(entity);
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_MACRO,
                    EVENT_TYPE,
                    snapshot.baseDate().toString());
            return new CollectResult(existing.isPresent() ? 0 : 1, existing.isPresent() ? 1 : 0);
        } catch (MacroGatewayException ex) {
            opsEventRecorder.recordFailure(
                    MarketDataOpsEventRecorder.SERVICE_MACRO,
                    EVENT_TYPE,
                    baseDate.toString(),
                    ex.getMessage(),
                    ex.getCategory().name());
            throw ex;
        }
    }

    public record CollectResult(int saved, int updated) {
    }
}
