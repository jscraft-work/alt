package work.jscraft.alt.news.application;

import java.time.OffsetDateTime;

public final class NewsExternalRecords {

    private NewsExternalRecords() {
    }

    public record ExternalNewsItem(
            String providerName,
            String externalNewsId,
            String title,
            String articleUrl,
            OffsetDateTime publishedAt,
            String summary) {
    }

    public record ExternalArticleBody(String bodyText) {
    }
}
