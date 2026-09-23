package com.coyotai.education.fee;

import com.coyotai.education.academic.AcademicYear;
import com.coyotai.education.academic.Course;
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
import java.util.ArrayList;
import java.util.List;

/**
 * A fee plan: what a student owes for a course/year, split into installments.
 * {@code totalAmount} is the course fee when the plan was created (from the course master);
 * the student's own discount, with its reason, gives {@code netAmount}.
 */
@Entity
@Table(name = "student_fees")
@Getter
@Setter
@NoArgsConstructor
public class StudentFee extends AuditedEntity {

    public enum Status { ACTIVE, CLOSED, CANCELLED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Why this student pays less, e.g. "Financial hardship" or "Merit scholarship". */
    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "studentFee", cascade = CascadeType.ALL)
    @OrderBy("installmentNo asc")
    private List<FeeInstallment> installments = new ArrayList<>();
}
