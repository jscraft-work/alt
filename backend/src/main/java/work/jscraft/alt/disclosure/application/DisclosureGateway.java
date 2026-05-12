package work.jscraft.alt.disclosure.application;

import java.time.OffsetDateTime;
import java.util.List;

import work.jscraft.alt.disclosure.application.DisclosureExternalRecords.ExternalDisclosureItem;

public interface DisclosureGateway {

    List<ExternalDisclosureItem> fetchDisclosures(String dartCorpCode, OffsetDateTime sinceInclusive);
}
