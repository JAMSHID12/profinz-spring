package com.coyotai.education.academic;

import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.common.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * A programme such as CA or ACCA. Master data per client - never a constant in code.
 * The course fee is the standard fee every student of the course is billed; individual
 * students differ only by their discount on the fee plan.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course extends AuditedEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    /** Standard fee for the course; null until the centre sets it. */
    @Column(name = "fee_amount", precision = 12, scale = 2)
    private BigDecimal feeAmount;

    /** How many installments a new fee plan is split into unless chosen otherwise. */
    @Column(name = "default_installments")
    private Integer defaultInstallments;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
