package work.jscraft.alt.disclosure.application;

import java.time.OffsetDateTime;

public final class DisclosureExternalRecords {

    private DisclosureExternalRecords() {
    }

    public record ExternalDisclosureItem(
            String dartCorpCode,
            String disclosureNo,
            String title,
            OffsetDateTime publishedAt,
            String previewText,
            String documentUrl) {
    }
}
