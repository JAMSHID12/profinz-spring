package com.coyotai.education.discipline;

import com.coyotai.education.common.AuditedEntity;
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

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "student_fines")
@Getter
@Setter
@NoArgsConstructor
public class StudentFine extends AuditedEntity {

    public enum Status { PENDING, PAID, WAIVED, CANCELLED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discipline_record_id")
    private DisciplineRecord disciplineRecord;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "fine_date", nullable = false)
    private LocalDate fineDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_reference", length = 60)
    private String paymentReference;

    @Column(length = 500)
    private String remarks;
}
