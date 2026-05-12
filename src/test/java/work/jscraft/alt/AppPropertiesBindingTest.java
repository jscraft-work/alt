package work.jscraft.alt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import work.jscraft.alt.common.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppPropertiesBindingTest {

    @Autowired
    private AppProperties appProperties;

    @Test
    void appPropertiesBindFromConfiguration() {
        assertThat(appProperties.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(appProperties.getRuntime().getRole()).isEqualTo(AppProperties.RuntimeRole.WEB_APP);
    }
}
