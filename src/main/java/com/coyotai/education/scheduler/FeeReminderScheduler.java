package com.coyotai.education.scheduler;

import com.coyotai.education.fee.FeeInstallment;
import com.coyotai.education.fee.FeeInstallmentRepository;
import com.coyotai.education.fee.FeeService;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily fee reminders: reminder 1 on the due date, 2 three days later, 3 seven days later,
 * then no more. "At least N days late" (not "exactly N") means a day the server was down
 * does not silently skip a reminder. Offsets come from {@code project.fees.reminder.offset-days}.
 */
@Component
public class FeeReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeeReminderScheduler.class);

    private final FeeInstallmentRepository installmentRepository;
    private final FeeService feeService;
    private final ProjectConfigService configService;

    public FeeReminderScheduler(FeeInstallmentRepository installmentRepository, FeeService feeService,
                                ProjectConfigService configService) {
        this.installmentRepository = installmentRepository;
        this.feeService = feeService;
        this.configService = configService;
    }

    @Scheduled(cron = "${project.fees.reminder.cron:0 0 9 * * *}", zone = "${project.client.timezone:UTC}")
    public void run() {
        if (!configService.fees().getReminder().isEnabled() || !configService.isModuleEnabled(ModuleCode.FEES)) {
            return;
        }
        try {
            log.info("Fee reminder run finished: {} reminders queued", createDueReminders());
        } catch (RuntimeException ex) {
            log.error("Fee reminder run failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public int createDueReminders() {
        feeService.refreshOverdue();
        List<Integer> offsets = configService.fees().getReminder().getOffsetDays();
        LocalDate today = configService.today();
        int queued = 0;
        for (FeeInstallment installment : installmentRepository.findReminderCandidates(today)) {
            int alreadySent = installment.getReminderCount();
            if (alreadySent >= offsets.size()) {
                continue;
            }
            long daysLate = ChronoUnit.DAYS.between(installment.getDueDate(), today);
            if (daysLate < offsets.get(alreadySent)) {
                continue;
            }
            if (feeService.queueReminder(installment, false)) {
                queued++;
            }
        }
        return queued;
    }
}
