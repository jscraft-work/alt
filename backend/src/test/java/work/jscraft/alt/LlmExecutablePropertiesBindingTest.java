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
    void openclawDefaultsBindFromApplicationYaml() {
        LlmExecutableProperties.Provider openclaw = llmExecutableProperties.getOpenclaw();
        assertThat(openclaw.getCommand()).isEqualTo("/usr/local/bin/openclaw");
        assertThat(openclaw.getTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void nanobotDefaultsBindFromApplicationYaml() {
        LlmExecutableProperties.Provider nanobot = llmExecutableProperties.getNanobot();
        assertThat(nanobot.getCommand()).isEqualTo("/usr/local/bin/nanobot");
        assertThat(nanobot.getTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void forProviderReturnsMatchingProvider() {
        LlmExecutableProperties.Provider openclaw = llmExecutableProperties.forProvider("openclaw");
        assertThat(openclaw).isSameAs(llmExecutableProperties.getOpenclaw());

        LlmExecutableProperties.Provider nanobot = llmExecutableProperties.forProvider("nanobot");
        assertThat(nanobot).isSameAs(llmExecutableProperties.getNanobot());
    }

    @Test
    void forProviderRejectsUnknownProvider() {
        assertThatThrownBy(() -> llmExecutableProperties.forProvider("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
