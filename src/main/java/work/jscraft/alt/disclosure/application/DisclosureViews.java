package work.jscraft.alt.disclosure.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class DisclosureViews {

    private DisclosureViews() {
    }

    public record DisclosureListItem(
            UUID id,
            String dartCorpCode,
            String symbolCode,
            String symbolName,
            String title,
            OffsetDateTime publishedAt,
            String previewText,
            String documentUrl) {
    }
}
