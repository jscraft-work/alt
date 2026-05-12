package work.jscraft.alt;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.ops.application.AppSeedProperties;
import work.jscraft.alt.ops.application.SeedDataRunner;
import work.jscraft.alt.ops.application.SeedDataRunner.SeedResult;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataBootstrapTest extends CollectorIntegrationTestSupport {

    @Autowired
    private SeedDataRunner seedDataRunner;

    @Autowired
    private AppSeedProperties seedProperties;

    @BeforeEach
    void setUp() {
        resetAdminCatalogState();
        seedProperties.setEnabled(true);
        seedProperties.getAdmin().setLoginId("admin");
        seedProperties.getAdmin().setPassword("ChangeMe!2026");
        seedProperties.getAdmin().setDisplayName("관리자");
    }

    @Test
    void firstRunCreatesAdminAndDefaultModelProfile() {
        SeedResult result = seedDataRunner.seed();

        assertThat(result.adminCreated()).isTrue();
        assertThat(result.modelProfileCreated()).isTrue();

        List<AppUserEntity> users = appUserRepository.findAll();
        assertThat(users).hasSize(1);
        AppUserEntity admin = users.get(0);
        assertThat(admin.getLoginId()).isEqualTo("admin");
        assertThat(admin.getRoleCode()).isEqualTo("ADMIN");
        assertThat(admin.isEnabled()).isTrue();
        // password is hashed
        assertThat(admin.getPasswordHash()).isNotBlank().doesNotContain("ChangeMe!2026");

        List<LlmModelProfileEntity> profiles = llmModelProfileRepository.findAll();
        assertThat(profiles).hasSize(1);
        LlmModelProfileEntity profile = profiles.get(0);
        assertThat(profile.getPurpose()).isEqualTo("trading_decision");
        assertThat(profile.isEnabled()).isTrue();
    }

    @Test
    void secondRunIsIdempotentAndCreatesNothing() {
        seedDataRunner.seed();

        SeedResult result = seedDataRunner.seed();
        assertThat(result.adminCreated()).isFalse();
        assertThat(result.modelProfileCreated()).isFalse();
        assertThat(appUserRepository.findAll()).hasSize(1);
        assertThat(llmModelProfileRepository.findAll()).hasSize(1);
    }

    @Test
    void disabledSeedDoesNotTouchDatabase() {
        seedProperties.setEnabled(false);

        SeedResult result = seedDataRunner.seed();

        assertThat(result.adminCreated()).isFalse();
        assertThat(result.modelProfileCreated()).isFalse();
        assertThat(appUserRepository.findAll()).isEmpty();
        assertThat(llmModelProfileRepository.findAll()).isEmpty();
    }
}
