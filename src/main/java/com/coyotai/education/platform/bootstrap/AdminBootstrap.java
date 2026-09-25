package com.coyotai.education.platform.bootstrap;

import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.auth.UserService;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RoleCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Creates the first administrator of an empty production database from
 * BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD. Never touches a database that has users.
 */
@Component
@Order(3)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final ProjectConfigService configService;

    public AdminBootstrap(UserRepository userRepository, UserService userService, ProjectConfigService configService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.configService = configService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String username = configService.bootstrap().getAdminUsername();
        String password = configService.bootstrap().getAdminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("The database has no users. Set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD "
                    + "and restart to create the first administrator.");
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least 8 characters");
        }
        userService.createAccount(username, password, "Administrator", null, null,
                Set.of(RoleCode.ADMINISTRATIVE), true);
        log.info("Created the first administrator '{}'. The password must be changed at first sign-in.", username);
    }
}
