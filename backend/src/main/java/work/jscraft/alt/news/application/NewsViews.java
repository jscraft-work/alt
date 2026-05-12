package work.jscraft.alt.news.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class NewsViews {

    private NewsViews() {
    }

    public record NewsListItem(
            UUID id,
            String providerName,
            String title,
            String articleUrl,
            OffsetDateTime publishedAt,
            String summary,
            String usefulnessStatus,
            List<RelatedAssetView> relatedAssets) {
    }

    public record RelatedAssetView(String symbolCode, String symbolName) {
    }
}
