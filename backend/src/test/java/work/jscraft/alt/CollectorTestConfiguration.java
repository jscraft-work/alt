package work.jscraft.alt;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class CollectorTestConfiguration {

    @Bean
    @Primary
    FakeMarketDataGateway fakeMarketDataGateway() {
        return new FakeMarketDataGateway();
    }

    @Bean
    @Primary
    FakeNewsGateway fakeNewsGateway() {
        return new FakeNewsGateway();
    }

    @Bean
    @Primary
    FakeDisclosureGateway fakeDisclosureGateway() {
        return new FakeDisclosureGateway();
    }

    @Bean
    @Primary
    FakeMacroGateway fakeMacroGateway() {
        return new FakeMacroGateway();
    }

    @Bean
    @Primary
    FakeNewsAssessmentEngine fakeNewsAssessmentEngine() {
        return new FakeNewsAssessmentEngine();
    }

    @Bean
    @Primary
    FakeTradingDecisionEngine fakeTradingDecisionEngine() {
        FakeTradingDecisionEngine fake = new FakeTradingDecisionEngine();
        fake.resetAll();
        return fake;
    }

    @Bean
    @Primary
    FakeBrokerGateway fakeBrokerGateway() {
        return new FakeBrokerGateway();
    }
}
