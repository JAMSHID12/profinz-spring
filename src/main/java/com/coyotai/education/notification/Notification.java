package com.coyotai.education.notification;

import com.coyotai.education.common.TenantEntity;
import com.coyotai.education.student.ParentContact;
import com.coyotai.education.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One message on one channel, and its delivery state. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends TenantEntity {

    public enum Status { PENDING, PROCESSING, SENT, FAILED, CANCELLED }

    public enum RecipientType { PARENT, STUDENT }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 40)
    private NotificationEvent eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private RecipientType recipientType;

    /** Account that sees an in-app message. */
    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @jakarta.persistence.Embedded
    private ParentContact parent;

    /** Phone number or e-mail address for external channels. */
    @Column(length = 150)
    private String destination;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Column(length = 200)
    private String title;

    @Column(name = "message_payload", nullable = false, columnDefinition = "TEXT")
    private String messagePayload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
