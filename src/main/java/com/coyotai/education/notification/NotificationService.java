package com.coyotai.education.notification;

import com.coyotai.education.common.PageResponse;
import com.coyotai.education.common.ResourceNotFoundException;
import com.coyotai.education.student.ParentContact;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.student.Student;
import com.coyotai.education.util.JsonUtils;
import com.coyotai.education.util.PhoneNumbers;
import com.coyotai.education.whatsapp.WhatsAppProperties;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The notification abstraction. Callers raise an event for a student; configuration decides
 * the channels. Publishing only inserts rows, so it is safe inside attendance or payment
 * transactions - delivery happens later in {@code NotificationQueueProcessor}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final NotificationRepository repository;
    private final ProjectConfigService configService;
    private final WhatsAppProperties whatsAppProperties;

    public NotificationService(NotificationRepository repository, ProjectConfigService configService,
                               WhatsAppProperties whatsAppProperties) {
        this.repository = repository;
        this.configService = configService;
        this.whatsAppProperties = whatsAppProperties;
    }

    /** Result of publishing one event: how many messages were queued and how many channels were skipped. */
    public record PublishResult(int queued, int skipped) {

        public boolean anyQueued() {
            return queued > 0;
        }
    }

    /** Can this parent receive WhatsApp messages at all? */
    public boolean canMessageParent(ParentContact parent) {
        return parent != null && parent.isActive() && parent.isWhatsappOptIn()
                && PhoneNumbers.hasText(parent.resolveWhatsappNumber());
    }

    @Transactional
    public PublishResult publish(NotificationEvent event, Student student, NotificationContent content) {
        if (!configService.isModuleEnabled(com.coyotai.education.platform.ModuleCode.NOTIFICATIONS)) {
            return new PublishResult(0, 0);
        }
        int queued = 0;
        int skipped = 0;
        for (NotificationChannel channel : configService.channelsFor(event)) {
            boolean created = switch (channel) {
                case WHATSAPP -> queueWhatsApp(event, student, content);
                case IN_APP -> queueInApp(event, student, content);
                case SMS, EMAIL -> queueUnsupported(event, student, content, channel);
            };
            if (created) {
                queued++;
            } else {
                skipped++;
            }
        }
        return new PublishResult(queued, skipped);
    }

    private boolean queueWhatsApp(NotificationEvent event, Student student, NotificationContent content) {
        if (!whatsAppProperties.supportsEvent(event)) return false;
        ParentContact parent = student.getParent();
        if (!canMessageParent(parent)) {
            return false;
        }
        Notification notification = base(event, NotificationChannel.WHATSAPP, Notification.RecipientType.PARENT, student, content);
        notification.setParent(ParentContact.copyOf(parent));
        notification.setDestination(parent.resolveWhatsappNumber());
        String templateName = whatsAppProperties.templateName(event);
        notification.setTemplateName(templateName);
        notification.setMessagePayload(JsonUtils.toJson(new NotificationPayload(
                templateName, whatsAppProperties.defaultLanguage(), content.parameters(), content.text())));
        repository.save(notification);
        return true;
    }

    private boolean queueInApp(NotificationEvent event, Student student, NotificationContent content) {
        if (student.getUser() == null || !student.getUser().isActive()) {
            return false;
        }
        Notification notification = base(event, NotificationChannel.IN_APP, Notification.RecipientType.STUDENT, student, content);
        notification.setRecipientUserId(student.getUser().getId());
        notification.setMessagePayload(JsonUtils.toJson(new NotificationPayload(null, null, List.of(), content.text())));
        repository.save(notification);
        return true;
    }

    private boolean queueUnsupported(NotificationEvent event, Student student, NotificationContent content,
                                     NotificationChannel channel) {
        // SMS and e-mail are part of the abstraction but have no provider yet; queueing them
        // makes the gap visible in the log instead of silently dropping the message.
        Notification notification = base(event, channel, Notification.RecipientType.PARENT, student, content);
        notification.setParent(ParentContact.copyOf(student.getParent()));
        notification.setMessagePayload(JsonUtils.toJson(new NotificationPayload(null, null, content.parameters(), content.text())));
        repository.save(notification);
        return true;
    }

    private Notification base(NotificationEvent event, NotificationChannel channel, Notification.RecipientType recipient,
                              Student student, NotificationContent content) {
        Notification notification = new Notification();
        notification.setEventType(event);
        notification.setChannel(channel);
        notification.setRecipientType(recipient);
        notification.setStudent(student);
        notification.setTitle(content.title());
        notification.setStatus(Notification.Status.PENDING);
        notification.setScheduledAt(Instant.now());
        notification.setCreatedAt(Instant.now());
        return notification;
    }

    // ---- Queue state transitions (each in its own short transaction) -------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Notification> claimDueBatch(Instant scheduledThrough) {
        List<Notification> due = repository.findDueForSending(scheduledThrough, whatsAppProperties.queue().maxRetries(),
                PageRequest.of(0, Math.max(1, whatsAppProperties.queue().batchSize())));
        due.forEach(notification -> notification.setStatus(Notification.Status.PROCESSING));
        return repository.saveAll(due);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        repository.findById(id).ifPresent(notification -> {
            notification.setStatus(Notification.Status.SENT);
            notification.setSentAt(Instant.now());
            notification.setErrorMessage(null);
        });
    }

    /** Stays FAILED; eligible again after the back-off until the retry limit is reached. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String error) {
        repository.findById(id).ifPresent(notification -> {
            int attempts = notification.getRetryCount() + 1;
            notification.setRetryCount(attempts);
            notification.setStatus(Notification.Status.FAILED);
            notification.setErrorMessage(truncate(error));
            notification.setScheduledAt(Instant.now().plus(
                    Math.max(1, whatsAppProperties.queue().retryBackoffMinutes()), ChronoUnit.MINUTES));
            if (attempts >= whatsAppProperties.queue().maxRetries()) {
                log.warn("Notification {} failed {} times and will not be retried automatically", id, attempts);
            }
        });
    }

    /** Manual retry from the message log: a fresh retry budget. */
    @Transactional
    public NotificationDtos.LogEntry retry(Long id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notification.setStatus(Notification.Status.PENDING);
        notification.setRetryCount(0);
        notification.setErrorMessage(null);
        notification.setScheduledAt(Instant.now());
        return NotificationDtos.LogEntry.from(notification);
    }

    // ---- Queries -------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<NotificationDtos.LogEntry> search(Notification.Status status, NotificationChannel channel,
                                                          NotificationEvent event, Long studentId, Pageable pageable) {
        return PageResponse.of(repository.search(status, channel, event, studentId, pageable),
                NotificationDtos.LogEntry::from);
    }

    @Transactional(readOnly = true)
    public List<NotificationDtos.LogEntry> recentExternal(int limit) {
        return repository.findRecentExternal(PageRequest.of(0, limit)).stream()
                .map(NotificationDtos.LogEntry::from).toList();
    }

    @Transactional(readOnly = true)
    public NotificationDtos.Inbox inbox(Long userId, int limit) {
        List<NotificationDtos.InboxItem> items = repository.findInbox(userId, PageRequest.of(0, limit)).stream()
                .map(NotificationDtos.InboxItem::from).toList();
        return new NotificationDtos.Inbox(repository.countUnread(userId), items);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public long countByStatus(Notification.Status status) {
        return repository.countByStatus(status);
    }

    /** Duplicate guard for scheduled reminders. */
    @Transactional(readOnly = true)
    public boolean hasEventSince(Long studentId, NotificationEvent event, Instant since) {
        return repository.countByStudentIdAndEventTypeAndCreatedAtAfter(studentId, event, since) > 0;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
