package work.jscraft.alt;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import work.jscraft.alt.disclosure.application.DisclosureExternalRecords.ExternalDisclosureItem;
import work.jscraft.alt.disclosure.application.DisclosureGateway;
import work.jscraft.alt.disclosure.application.DisclosureGatewayException;

class FakeDisclosureGateway implements DisclosureGateway {

    private final Map<String, List<ExternalDisclosureItem>> responses = new HashMap<>();
    private final Map<String, DisclosureGatewayException> failures = new HashMap<>();

    void resetAll() {
        responses.clear();
        failures.clear();
    }

    void primeDisclosures(String dartCorpCode, List<ExternalDisclosureItem> items) {
        responses.put(dartCorpCode, new ArrayList<>(items));
        failures.remove(dartCorpCode);
    }

    void primeFailure(String dartCorpCode, DisclosureGatewayException exception) {
        failures.put(dartCorpCode, exception);
        responses.remove(dartCorpCode);
    }

    @Override
    public List<ExternalDisclosureItem> fetchDisclosures(String dartCorpCode, OffsetDateTime sinceInclusive) {
        DisclosureGatewayException failure = failures.get(dartCorpCode);
        if (failure != null) {
            throw failure;
        }
        List<ExternalDisclosureItem> response = responses.get(dartCorpCode);
        return response == null ? List.of() : new ArrayList<>(response);
    }
}
