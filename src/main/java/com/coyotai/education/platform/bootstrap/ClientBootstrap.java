package com.coyotai.education.platform.bootstrap;

import com.coyotai.education.assessment.ExamType;
import com.coyotai.education.assessment.ExamTypeRepository;
import com.coyotai.education.auth.PermissionCode;
import com.coyotai.education.auth.RoleRepository;
import com.coyotai.education.discipline.DisciplineType;
import com.coyotai.education.discipline.DisciplineTypeRepository;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.tenant.ClientRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs first on every start: registers the configured client, gives a brand-new client its
 * starter master lists (which it can then edit freely), and refuses to start with a role
 * configuration that would leave nobody able to administer users.
 */
@Component
@Order(1)
public class ClientBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientBootstrap.class);

    private final ClientRegistry clientRegistry;
    private final ProjectConfigService configService;
    private final RoleRepository roleRepository;
    private final DisciplineTypeRepository disciplineTypeRepository;
    private final ExamTypeRepository examTypeRepository;
    private final com.coyotai.education.student.EducationCategoryRepository educationCategories;

    public ClientBootstrap(ClientRegistry clientRegistry, ProjectConfigService configService,
                           RoleRepository roleRepository, DisciplineTypeRepository disciplineTypeRepository,
                           ExamTypeRepository examTypeRepository, com.coyotai.education.student.EducationCategoryRepository educationCategories) {
        this.clientRegistry = clientRegistry;
        this.configService = configService;
        this.roleRepository = roleRepository;
        this.disciplineTypeRepository = disciplineTypeRepository;
        this.examTypeRepository = examTypeRepository;
        this.educationCategories = educationCategories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long clientId = clientRegistry.configuredClientId();
        validateRoleConfiguration();
        if (educationCategories.count() == 0) {
            String[][] defaults = {{"PLUS_TWO", "+2 Completed Students"}, {"DEGREE", "Degree Completed Students"}};
            for (int i = 0; i < defaults.length; i++) {
                var category = new com.coyotai.education.student.EducationCategory();
                category.setCode(defaults[i][0]); category.setName(defaults[i][1]); category.setDisplayOrder(i + 1);
                educationCategories.save(category);
            }
        }
        if (disciplineTypeRepository.count() == 0) {
            seedDisciplineTypes();
        }
        if (examTypeRepository.count() == 0) {
            seedExamTypes();
        }
        log.info("Serving client {} ({}), id {}. Enabled roles: {}. Enabled modules: {}.",
                configService.getClientName(), configService.getClientCode(), clientId,
                configService.enabledRoles(), configService.enabledModules());
    }

    /** At least one enabled role must be able to manage users, or nobody could fix anything. */
    private void validateRoleConfiguration() {
        boolean manageable = roleRepository.findAllWithPermissions().stream()
                .filter(role -> configService.isRoleEnabled(role.getCode()))
                .anyMatch(role -> role.getPermissions().stream()
                        .anyMatch(p -> PermissionCode.USER_MANAGE.name().equals(p.getCode())));
        if (!manageable) {
            throw new IllegalStateException("No enabled role can manage users. Enable project.role.administrative "
                    + "(or another role holding USER_MANAGE) in the configuration.");
        }
    }

    private void seedDisciplineTypes() {
        String[][] defaults = {
                {"ATTENDANCE", "Attendance"},
                {"UNIFORM", "Uniform"},
                {"NAME_BADGE", "Tag / Name badge"},
                {"LATE_COMING", "Late coming"},
                {"OTHER", "Other"},
        };
        for (int i = 0; i < defaults.length; i++) {
            DisciplineType type = new DisciplineType();
            type.setCode(defaults[i][0]);
            type.setName(defaults[i][1]);
            type.setDisplayOrder(i + 1);
            type.setActive(true);
            disciplineTypeRepository.save(type);
        }
        log.info("Created the starter discipline types; edit them under Master data");
    }

    private void seedExamTypes() {
        String[][] defaults = {
                {"CLASS_EXAM", "Class exam"},
                {"MODEL_EXAM", "Model exam"},
                {"COURSE_EXAM", "Course exam"},
        };
        for (int i = 0; i < defaults.length; i++) {
            ExamType type = new ExamType();
            type.setCode(defaults[i][0]);
            type.setName(defaults[i][1]);
            type.setDisplayOrder(i + 1);
            type.setActive(true);
            examTypeRepository.save(type);
        }
        log.info("Created the starter exam types; edit them under Master data");
    }
}
