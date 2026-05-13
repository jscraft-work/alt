package work.jscraft.alt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import work.jscraft.alt.trading.application.decision.LlmExecutableProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LlmExecutablePropertiesBindingTest {

    @Autowired
    private LlmExecutableProperties llmExecutableProperties;

    @Test
    void baseUrlBindsFromApplicationYaml() {
        assertThat(llmExecutableProperties.getBaseUrl())
                .isEqualTo("http://host.docker.internal:18000");
    }

    @Test
    void openclawTimeoutBindsFromApplicationYaml() {
        assertThat(llmExecutableProperties.getOpenclaw().getTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void nanobotTimeoutBindsFromApplicationYaml() {
        assertThat(llmExecutableProperties.getNanobot().getTimeoutSeconds()).isEqualTo(120);
    }

    @Test
    void forProviderReturnsMatchingProvider() {
        assertThat(llmExecutableProperties.forProvider("openclaw"))
                .isSameAs(llmExecutableProperties.getOpenclaw());
        assertThat(llmExecutableProperties.forProvider("nanobot"))
                .isSameAs(llmExecutableProperties.getNanobot());
    }

    @Test
    void forProviderRejectsUnknownProvider() {
        assertThatThrownBy(() -> llmExecutableProperties.forProvider("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void levelOfMapsProviderToWrapperLevel() {
        assertThat(LlmExecutableProperties.levelOf("openclaw")).isEqualTo("normal");
        assertThat(LlmExecutableProperties.levelOf("nanobot")).isEqualTo("high");
        assertThatThrownBy(() -> LlmExecutableProperties.levelOf("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
