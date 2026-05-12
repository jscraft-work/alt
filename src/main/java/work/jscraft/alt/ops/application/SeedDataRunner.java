package work.jscraft.alt.ops.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import work.jscraft.alt.auth.infrastructure.persistence.AppUserEntity;
import work.jscraft.alt.auth.infrastructure.persistence.AppUserRepository;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileEntity;
import work.jscraft.alt.llm.infrastructure.persistence.LlmModelProfileRepository;

@Component
@Order(1)
public class SeedDataRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private static final String ROLE_ADMIN = "ADMIN";

    private final AppSeedProperties properties;
    private final AppUserRepository appUserRepository;
    private final LlmModelProfileRepository llmModelProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataRunner(
            AppSeedProperties properties,
            AppUserRepository appUserRepository,
            LlmModelProfileRepository llmModelProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
        this.llmModelProfileRepository = llmModelProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed();
    }

    @Transactional
    public SeedResult seed() {
        if (!properties.isEnabled()) {
            return new SeedResult(false, false);
        }
        boolean adminCreated = ensureAdminUser();
        boolean modelProfileCreated = ensureDefaultModelProfile();
        return new SeedResult(adminCreated, modelProfileCreated);
    }

    private boolean ensureAdminUser() {
        AppSeedProperties.Admin admin = properties.getAdmin();
        if (appUserRepository.findByLoginId(admin.getLoginId()).isPresent()) {
            return false;
        }
        AppUserEntity user = new AppUserEntity();
        user.setLoginId(admin.getLoginId());
        user.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
        user.setDisplayName(admin.getDisplayName());
        user.setRoleCode(ROLE_ADMIN);
        user.setEnabled(true);
        appUserRepository.saveAndFlush(user);
        log.info("seed admin user created loginId={}", admin.getLoginId());
        return true;
    }

    private boolean ensureDefaultModelProfile() {
        AppSeedProperties.ModelProfile config = properties.getModelProfile();
        List<LlmModelProfileEntity> existing = llmModelProfileRepository.findByPurposeAndEnabledTrue(config.getPurpose());
        if (!existing.isEmpty()) {
            return false;
        }
        LlmModelProfileEntity profile = new LlmModelProfileEntity();
        profile.setPurpose(config.getPurpose());
        profile.setProvider(config.getProvider());
        profile.setModelName(config.getModelName());
        profile.setEnabled(true);
        llmModelProfileRepository.saveAndFlush(profile);
        log.info("seed default trading model profile created provider={} model={}",
                config.getProvider(), config.getModelName());
        return true;
    }

    public record SeedResult(boolean adminCreated, boolean modelProfileCreated) {
    }
}
