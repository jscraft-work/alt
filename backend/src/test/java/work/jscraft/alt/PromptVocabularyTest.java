package work.jscraft.alt;

import java.util.List;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.strategy.application.PromptVocabulary;
import work.jscraft.alt.strategy.application.PromptVocabulary.SourceCatalog;
import work.jscraft.alt.strategy.application.PromptVocabulary.SourceParameter;
import work.jscraft.alt.strategy.application.PromptVocabulary.StockField;
import work.jscraft.alt.strategy.application.PromptVocabulary.SystemVariable;
import work.jscraft.alt.strategy.application.PromptVocabularyProvider;

import static org.assertj.core.api.Assertions.assertThat;

class PromptVocabularyTest {

    private final PromptVocabularyProvider provider = new PromptVocabularyProvider();

    @Test
    void vocabularyIsNotEmpty() {
        PromptVocabulary vocabulary = provider.vocabulary();

        assertThat(vocabulary.systemVariables()).isNotEmpty();
        assertThat(vocabulary.stocksCollection()).isNotNull();
        assertThat(vocabulary.stocksCollection().fields()).isNotEmpty();
        assertThat(vocabulary.globalVariables()).isNotEmpty();
        assertThat(vocabulary.sources()).isNotEmpty();
    }

    @Test
    void systemVariablesContainCoreNames() {
        List<String> names = provider.vocabulary().systemVariables().stream()
                .map(SystemVariable::name)
                .toList();

        assertThat(names).contains("current_time", "cash_amount", "held_positions");
    }

    @Test
    void stocksCollectionExposesPerStockFields() {
        List<String> fieldNames = provider.vocabulary().stocksCollection().fields().stream()
                .map(StockField::name)
                .toList();

        assertThat(provider.vocabulary().stocksCollection().name()).isEqualTo("stocks");
        assertThat(fieldNames).contains(
                "minute_bars",
                "daily_bars",
                "fundamental",
                "news",
                "disclosures",
                "orderbook",
                "trade_history");
    }

    @Test
    void sourcesCatalogIncludesAllFrontmatterKeys() {
        List<String> sourceTypes = provider.vocabulary().sources().stream()
                .map(SourceCatalog::type)
                .toList();

        assertThat(sourceTypes).containsExactlyInAnyOrder(
                "minute_bars",
                "daily_bars",
                "fundamental",
                "news_hours",
                "disclosure_hours",
                "trade_history_days",
                "macro",
                "orderbook");
    }

    @Test
    void minuteBarsSourceDeclaresLookbackParameter() {
        SourceCatalog minuteBars = provider.vocabulary().sources().stream()
                .filter(s -> s.type().equals("minute_bars"))
                .findFirst()
                .orElseThrow();

        SourceParameter parameter = minuteBars.parameters().get(0);
        assertThat(parameter.name()).isEqualTo("minute_bars");
        assertThat(parameter.type()).isEqualTo("int");
    }
}
