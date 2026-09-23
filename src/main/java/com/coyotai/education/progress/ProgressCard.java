package com.coyotai.education.progress;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.assessment.PublicationStatus;
import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.student.Student;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of a student's progress over a period, with the mentor's remarks. The figures are
 * frozen when generated so a published card never changes under the student's feet.
 */
@Entity
@Table(name = "progress_cards")
@Getter
@Setter
@NoArgsConstructor
public class ProgressCard extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "attendance_percentage", precision = 5, scale = 2)
    private BigDecimal attendancePercentage;

    @Column(name = "daily_test_average", precision = 5, scale = 2)
    private BigDecimal dailyTestAverage;

    @Column(name = "weekly_test_average", precision = 5, scale = 2)
    private BigDecimal weeklyTestAverage;

    @Column(name = "exam_average", precision = 5, scale = 2)
    private BigDecimal examAverage;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Column(length = 10)
    private String grade;

    @Column(name = "syllabus_completion", precision = 5, scale = 2)
    private BigDecimal syllabusCompletion;

    @Column(name = "discipline_count", nullable = false)
    private int disciplineCount;

    @Column(name = "pending_fine_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pendingFineAmount = BigDecimal.ZERO;

    @Column(name = "mentor_remarks", columnDefinition = "TEXT")
    private String mentorRemarks;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PublicationStatus status = PublicationStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    @OneToMany(mappedBy = "progressCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<ProgressCardItem> items = new ArrayList<>();
}
