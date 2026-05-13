package work.jscraft.alt.strategy.application;

import java.util.List;

public record PromptVocabulary(
        List<SystemVariable> systemVariables,
        StocksCollection stocksCollection,
        List<GlobalVariable> globalVariables,
        List<SourceCatalog> sources) {

    public record SystemVariable(String name, String description, String example) {}

    public record StocksCollection(
            String name,
            String description,
            List<StockField> fields) {}

    public record StockField(String name, String description, String requiredSource) {}

    public record GlobalVariable(String name, String description, String requiredSource) {}

    public record SourceCatalog(
            String type,
            String description,
            List<SourceParameter> parameters) {}

    public record SourceParameter(String name, String type, String defaultValue, String description) {}
}
