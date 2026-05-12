package work.jscraft.alt;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import work.jscraft.alt.news.application.NewsExternalRecords.ExternalArticleBody;
import work.jscraft.alt.news.application.NewsExternalRecords.ExternalNewsItem;
import work.jscraft.alt.news.application.NewsGateway;
import work.jscraft.alt.news.application.NewsGatewayException;

class FakeNewsGateway implements NewsGateway {

    private final Map<String, List<ExternalNewsItem>> newsResponses = new HashMap<>();
    private final Map<String, NewsGatewayException> newsFailures = new HashMap<>();
    private final Map<String, ExternalArticleBody> bodyResponses = new HashMap<>();
    private final Map<String, NewsGatewayException> bodyFailures = new HashMap<>();

    void resetAll() {
        newsResponses.clear();
        newsFailures.clear();
        bodyResponses.clear();
        bodyFailures.clear();
    }

    void primeNews(String symbolCode, List<ExternalNewsItem> items) {
        newsResponses.put(symbolCode, new ArrayList<>(items));
        newsFailures.remove(symbolCode);
    }

    void primeNewsFailure(String symbolCode, NewsGatewayException exception) {
        newsFailures.put(symbolCode, exception);
        newsResponses.remove(symbolCode);
    }

    void primeBody(String url, ExternalArticleBody body) {
        bodyResponses.put(url, body);
        bodyFailures.remove(url);
    }

    void primeBodyFailure(String url, NewsGatewayException exception) {
        bodyFailures.put(url, exception);
        bodyResponses.remove(url);
    }

    @Override
    public List<ExternalNewsItem> fetchNews(String symbolCode, OffsetDateTime sinceInclusive) {
        NewsGatewayException failure = newsFailures.get(symbolCode);
        if (failure != null) {
            throw failure;
        }
        List<ExternalNewsItem> response = newsResponses.get(symbolCode);
        return response == null ? List.of() : new ArrayList<>(response);
    }

    @Override
    public Optional<ExternalArticleBody> fetchArticleBody(String articleUrl) {
        NewsGatewayException failure = bodyFailures.get(articleUrl);
        if (failure != null) {
            throw failure;
        }
        return Optional.ofNullable(bodyResponses.get(articleUrl));
    }
}
