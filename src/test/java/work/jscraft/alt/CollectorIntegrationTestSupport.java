package work.jscraft.alt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(CollectorTestConfiguration.class)
abstract class CollectorIntegrationTestSupport extends AdminCatalogApiIntegrationTestSupport {

    @Autowired
    protected FakeMarketDataGateway fakeMarketDataGateway;

    @Autowired
    protected FakeNewsGateway fakeNewsGateway;

    @Autowired
    protected FakeDisclosureGateway fakeDisclosureGateway;

    @Autowired
    protected FakeMacroGateway fakeMacroGateway;

    @Autowired
    protected FakeNewsAssessmentEngine fakeNewsAssessmentEngine;

    @Autowired
    protected FakeTradingDecisionEngine fakeTradingDecisionEngine;

    @Autowired
    protected FakeBrokerGateway fakeBrokerGateway;
}
