package com.coyotai.education.assessment;

import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.common.AuditedEntity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A daily or weekly test for one batch and subject (table {@code tests}). */
@Entity
@Table(name = "tests")
@Getter
@Setter
@NoArgsConstructor
public class AcademicTest extends AuditedEntity {

    public enum Type { DAILY, WEEKLY }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "test_type", nullable = false, length = 20)
    private Type testType;

    @Column(nullable = false, length = 150)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(name = "max_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal maxMarks;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PublicationStatus status = PublicationStatus.DRAFT;

    @Column(length = 500)
    private String remarks;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;
}
