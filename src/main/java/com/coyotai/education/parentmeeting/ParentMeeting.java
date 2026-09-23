package com.coyotai.education.parentmeeting;

import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.student.ParentContact;
import com.coyotai.education.staff.Mentor;
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

import java.time.LocalDate;

@Entity
@Table(name = "parent_meetings")
@Getter
@Setter
@NoArgsConstructor
public class ParentMeeting extends AuditedEntity {

    public enum Status { SCHEDULED, COMPLETED, CANCELLED, FOLLOW_UP }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @jakarta.persistence.Embedded
    private ParentContact parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id")
    private Mentor mentor;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(columnDefinition = "TEXT")
    private String discussion;

    @Column(name = "academic_issues", length = 1000)
    private String academicIssues;

    @Column(name = "attendance_issues", length = 1000)
    private String attendanceIssues;

    @Column(name = "discipline_issues", length = 1000)
    private String disciplineIssues;

    @Column(name = "action_items", length = 1000)
    private String actionItems;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;
}
