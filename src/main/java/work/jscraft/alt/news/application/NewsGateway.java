package work.jscraft.alt.news.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalNewsItem;

public interface NewsGateway {

    List<ExternalNewsItem> fetchNews(String symbolCode, OffsetDateTime sinceInclusive);

    Optional<ExternalArticleBody> fetchArticleBody(String articleUrl);
}
