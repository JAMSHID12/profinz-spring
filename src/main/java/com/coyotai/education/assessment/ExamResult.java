package com.coyotai.education.assessment;

import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "exam_results")
@Getter
@Setter
@NoArgsConstructor
public class ExamResult extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "marks_obtained", precision = 7, scale = 2)
    private BigDecimal marksObtained;

    @Column(nullable = false)
    private boolean absent;

    /** Computed from the configured grade bands when marks are saved. */
    @Column(length = 10)
    private String grade;

    @Column(length = 255)
    private String remarks;
}
