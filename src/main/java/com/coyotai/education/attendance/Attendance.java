package com.coyotai.education.attendance;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.schedule.ClassSchedule;
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
import java.time.LocalDate;

/**
 * One student's attendance for a day or for one scheduled class. {@code sessionKey} is the
 * schedule id (0 for whole-day), which lets the database refuse duplicates.
 */
@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class Attendance extends AuditedEntity {

    public static final long WHOLE_DAY = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_schedule_id")
    private ClassSchedule classSchedule;

    @Column(name = "session_key", nullable = false)
    private long sessionKey = WHOLE_DAY;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    /** How late the student arrived; only for LATE. */
    @Column(name = "late_minutes")
    private Integer lateMinutes;

    /** Only for ABSENT and EXCUSED; the converter also reads pre-migration reason codes. */
    @jakarta.persistence.Convert(converter = AbsenceReasonConverter.class)
    @Column(name = "absence_reason", length = 20)
    private AbsenceReason absenceReason;

    /** Observations made while taking attendance; each also becomes a discipline record. */
    @Column(name = "no_uniform", nullable = false)
    private boolean noUniform;

    @Column(name = "no_id_tag", nullable = false)
    private boolean noIdTag;

    @Column(length = 255)
    private String remarks;

    @Column(name = "marked_by")
    private Long markedBy;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_status", nullable = false, length = 20)
    private NotificationDecision notificationStatus = NotificationDecision.NOT_REQUIRED;

    /** Whether a parent message was queued when this mark was saved. */
    public enum NotificationDecision { NOT_REQUIRED, QUEUED, SKIPPED }
}
