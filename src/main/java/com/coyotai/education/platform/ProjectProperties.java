package com.coyotai.education.platform;

import com.coyotai.education.notification.NotificationChannel;
import com.coyotai.education.notification.NotificationEvent;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that makes one client different from another, bound from {@code project.*}.
 * Read it through {@link ProjectConfigService} rather than injecting individual values.
 *
 * <pre>
 * ProjectProperties
 *  ├── ClientProperties     who the client is
 *  ├── BrandingProperties   how the client looks
 *  ├── module               which business areas exist
 *  ├── role                 which kinds of user may sign in
 *  └── academics, notifications, fees, student, bootstrap
 * </pre>
 */
@ConfigurationProperties(prefix = "project")
@Getter
@Setter
public class ProjectProperties {

    private ClientProperties client = new ClientProperties();
    private BrandingProperties branding = new BrandingProperties();
    private Map<ModuleCode, Toggle> module = new LinkedHashMap<>();
    private Map<RoleCode, Toggle> role = new LinkedHashMap<>();
    private AcademicsProperties academics = new AcademicsProperties();
    private StudentProperties student = new StudentProperties();
    private NotificationProperties notifications = new NotificationProperties();
    private FeeProperties fees = new FeeProperties();
    private BootstrapProperties bootstrap = new BootstrapProperties();

    @Getter
    @Setter
    public static class Toggle {
        private boolean enabled;
    }

    @Getter
    @Setter
    public static class AcademicsProperties {
        private PerformanceProperties performance = new PerformanceProperties();
        private boolean studentSyllabusVisible = true;
    }

    @Getter
    @Setter
    public static class PerformanceProperties {
        private boolean enabled = true;
        private Weights weights = new Weights();
        private List<GradeBand> grades = new ArrayList<>();
    }

    /** Relative weights of the performance components; they do not need to add up to 100. */
    @Getter
    @Setter
    public static class Weights {
        private int dailyTest = 20;
        private int weeklyTest = 20;
        private int exam = 50;
        private int attendance = 10;
    }

    @Getter
    @Setter
    public static class GradeBand {
        private String grade;
        private double min;
    }

    @Getter
    @Setter
    public static class StudentProperties {
        private String defaultPassword = "Welcome@123";
    }

    @Getter
    @Setter
    public static class NotificationProperties {
        private Map<NotificationChannel, Boolean> channels = new LinkedHashMap<>();
        private Map<NotificationEvent, List<NotificationChannel>> events = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class FeeProperties {
        private String receiptPrefix;
        private ReminderProperties reminder = new ReminderProperties();
    }

    @Getter
    @Setter
    public static class ReminderProperties {
        private boolean enabled = true;
        private String cron = "0 0 9 * * *";
        private List<Integer> offsetDays = new ArrayList<>(List.of(0, 3, 7));
    }

    @Getter
    @Setter
    public static class BootstrapProperties {
        private String adminUsername;
        private String adminPassword;
        private boolean seedSampleData;
    }
}
