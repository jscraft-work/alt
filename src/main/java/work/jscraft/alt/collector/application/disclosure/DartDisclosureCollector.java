package work.jscraft.alt.collector.application.disclosure;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.disclosure.application.DisclosureExternalRecords.ExternalDisclosureItem;
import work.jscraft.alt.disclosure.application.DisclosureGateway;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemEntity;
import work.jscraft.alt.disclosure.infrastructure.persistence.DisclosureItemRepository;

@Component
public class DartDisclosureCollector {

    public static final String EVENT_TYPE = "disclosure.collect";

    private final DisclosureGateway disclosureGateway;
    private final DisclosureItemRepository disclosureItemRepository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public DartDisclosureCollector(
            DisclosureGateway disclosureGateway,
            DisclosureItemRepository disclosureItemRepository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.disclosureGateway = disclosureGateway;
        this.disclosureItemRepository = disclosureItemRepository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public CollectResult collect(String dartCorpCode, OffsetDateTime sinceInclusive) {
        try {
            List<ExternalDisclosureItem> items = disclosureGateway.fetchDisclosures(dartCorpCode, sinceInclusive);
            int saved = 0;
            int skipped = 0;
            for (ExternalDisclosureItem item : items) {
                if (disclosureItemRepository.existsByDisclosureNo(item.disclosureNo())) {
                    skipped++;
                    continue;
                }
                DisclosureItemEntity entity = new DisclosureItemEntity();
                entity.setDartCorpCode(item.dartCorpCode());
                entity.setDisclosureNo(item.disclosureNo());
                entity.setTitle(item.title());
                entity.setPublishedAt(item.publishedAt());
                entity.setPreviewText(item.previewText());
                entity.setDocumentUrl(item.documentUrl());
                disclosureItemRepository.saveAndFlush(entity);
                saved++;
            }
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_DISCLOSURE,
                    EVENT_TYPE,
                    dartCorpCode);
            return new CollectResult(saved, skipped);
        } catch (DisclosureGatewayException ex) {
            opsEventRecorder.recordFailure(
                    MarketDataOpsEventRecorder.SERVICE_DISCLOSURE,
                    EVENT_TYPE,
                    dartCorpCode,
                    ex.getMessage(),
                    ex.getCategory().name());
            throw ex;
        }
    }

    public record CollectResult(int saved, int skipped) {
    }
}
