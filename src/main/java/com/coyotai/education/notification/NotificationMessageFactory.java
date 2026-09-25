package com.coyotai.education.notification;

import com.coyotai.education.platform.ProjectConfigService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds message content for every event. Template variables and their order are defined
 * here once; the centre name always comes from configuration.
 */
@Component
public class NotificationMessageFactory {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final ProjectConfigService configService;

    public NotificationMessageFactory(ProjectConfigService configService) {
        this.configService = configService;
    }

    /** {{1}} parent, {{2}} student, {{3}} class, {{4}} date, {{5}} centre. */
    public NotificationContent absent(String parent, String student, String className, LocalDate date) {
        return content("Absent on " + date(date),
                List.of(parent, student, dash(className), date(date), centre()),
                "Dear " + parent + ",\n\nThis is an attendance update from " + centre() + ".\n\n"
                        + student + " was marked absent from " + dash(className) + " on " + date(date)
                        + ".\n\nIf you believe this record is incorrect, please contact the centre office.\n\nThank you.");
    }

    /** {{1}} parent, {{2}} student, {{3}} class, {{4}} date, {{5}} centre. */
    public NotificationContent late(String parent, String student, String className, LocalDate date) {
        return content("Late on " + date(date),
                List.of(parent, student, dash(className), date(date), centre()),
                "Dear " + parent + ", " + student + " arrived late for " + dash(className)
                        + " on " + date(date) + ". - " + centre());
    }

    /** {{1}} parent, {{2}} student, {{3}} installment, {{4}} pending amount, {{5}} due date, {{6}} centre. */
    public NotificationContent feeDue(String parent, String student, String installment, BigDecimal pending,
                                      LocalDate dueDate) {
        return content("Fee due: " + installment,
                List.of(parent, student, installment, money(pending), date(dueDate), centre()),
                "Dear " + parent + ", the fee installment \"" + installment + "\" for " + student
                        + " has a pending balance of " + money(pending) + " due on " + date(dueDate) + ". - " + centre());
    }

    /** {{1}} parent, {{2}} student, {{3}} amount, {{4}} installment, {{5}} balance, {{6}} receipt, {{7}} centre. */
    public NotificationContent paymentReceived(String parent, String student, BigDecimal amount, String installment,
                                               BigDecimal balance, String receiptNumber) {
        return content("Payment received",
                List.of(parent, student, money(amount), installment, money(balance), receiptNumber, centre()),
                "Dear " + parent + ", we received " + money(amount) + " towards \"" + installment + "\" for "
                        + student + " (receipt " + receiptNumber + "). Balance: " + money(balance) + ". - " + centre());
    }

    /** {{1}} parent, {{2}} student, {{3}} exam, {{4}} subject, {{5}} marks, {{6}} grade, {{7}} centre. */
    public NotificationContent examResult(String parent, String student, String exam, String subject,
                                          String marks, String grade) {
        return content("Result published: " + exam,
                List.of(parent, student, exam, subject, marks, dash(grade), centre()),
                "Dear " + parent + ", the result of " + exam + " (" + subject + ") for " + student
                        + " is published: " + marks + (grade == null ? "" : ", grade " + grade) + ". - " + centre());
    }

    /** {{1}} parent, {{2}} student, {{3}} progress card, {{4}} score, {{5}} grade, {{6}} centre. */
    public NotificationContent progressCard(String parent, String student, String title, String score, String grade) {
        return content("Progress card published",
                List.of(parent, student, title, score, dash(grade), centre()),
                "Dear " + parent + ", the progress card \"" + title + "\" for " + student
                        + " is now available. Overall: " + score + (grade == null ? "" : " (" + grade + ")")
                        + ". - " + centre());
    }

    /** {{1}} parent, {{2}} student, {{3}} reason, {{4}} amount, {{5}} due date, {{6}} centre. */
    public NotificationContent fine(String parent, String student, String reason, BigDecimal amount, LocalDate dueDate) {
        return content("Fine: " + reason,
                List.of(parent, student, reason, money(amount), dueDate == null ? "-" : date(dueDate), centre()),
                "Dear " + parent + ", a fine of " + money(amount) + " was recorded for " + student + " (" + reason + ")"
                        + (dueDate == null ? "" : ", payable by " + date(dueDate)) + ". - " + centre());
    }

    /** {{1}} student, {{2}} subject, {{3}} date, {{4}} time, {{5}} change, {{6}} centre. */
    public NotificationContent scheduleChanged(String student, String subject, LocalDate date, LocalTime start,
                                               String change) {
        return content("Class " + change + ": " + subject,
                List.of(student, subject, date(date), start == null ? "-" : TIME.format(start), change, centre()),
                subject + " class on " + date(date) + (start == null ? "" : " at " + TIME.format(start))
                        + " has been " + change + ".");
    }

    /** {{1}} parent, {{2}} message, {{3}} centre. */
    public NotificationContent general(String parent, String message) {
        return content("Message from " + centre(), List.of(parent, message, centre()),
                "Dear " + parent + ", " + message + " - " + centre());
    }

    private NotificationContent content(String title, List<String> parameters, String text) {
        return new NotificationContent(title, parameters, text);
    }

    private String centre() {
        return configService.getClientName();
    }

    private String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    private String money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String dash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
