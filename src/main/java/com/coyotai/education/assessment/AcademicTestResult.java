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
@Table(name = "test_results")
@Getter
@Setter
@NoArgsConstructor
public class AcademicTestResult extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private AcademicTest test;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** Null when absent. Always between 0 and the test's maximum. */
    @Column(name = "marks_obtained", precision = 7, scale = 2)
    private BigDecimal marksObtained;

    @Column(nullable = false)
    private boolean absent;

    @Column(length = 255)
    private String remarks;
}
