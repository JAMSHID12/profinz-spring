package com.coyotai.education.notification;

import com.coyotai.education.util.JsonUtils;
import com.coyotai.education.util.PhoneNumbers;
import com.coyotai.education.whatsapp.dto.NotificationPayload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record LogEntry(Long id, NotificationEvent eventType, NotificationChannel channel,
                           Notification.RecipientType recipientType, Long studentId, String studentName,
                           String parentName, String destination, String templateName, String title,
                           String preview, Notification.Status status, int retryCount, String errorMessage,
                           Instant scheduledAt, Instant sentAt, Instant createdAt) {

        public static LogEntry from(Notification n) {
            return new LogEntry(n.getId(), n.getEventType(), n.getChannel(), n.getRecipientType(),
                    n.getStudent() == null ? null : n.getStudent().getId(),
                    n.getStudent() == null ? null : n.getStudent().getFullName(),
                    n.getParent() == null ? null : n.getParent().getName(),
                    n.getDestination(), n.getTemplateName(), n.getTitle(), previewOf(n), n.getStatus(),
                    n.getRetryCount(), n.getErrorMessage(), n.getScheduledAt(), n.getSentAt(), n.getCreatedAt());
        }
    }

    public record InboxItem(Long id, NotificationEvent eventType, String title, String message, Instant createdAt,
                            boolean read) {

        static InboxItem from(Notification n) {
            return new InboxItem(n.getId(), n.getEventType(), n.getTitle(), previewOf(n), n.getCreatedAt(),
                    n.getReadAt() != null);
        }
    }

    public record Inbox(long unread, List<InboxItem> items) {
    }

    public record TestMessageRequest(
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = PhoneNumbers.PATTERN, message = "Enter a valid phone number")
            String phoneNumber,
            @Size(max = 200, message = "Message is too long") String message,
            @Size(max = 20) List<@NotBlank @Size(max = 1024) String> parameters
    ) {
    }

    static String previewOf(Notification notification) {
        try {
            return JsonUtils.fromJson(notification.getMessagePayload(), NotificationPayload.class).preview();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
