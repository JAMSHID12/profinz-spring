package com.coyotai.education.progress;

import com.coyotai.education.academic.Subject;
import com.coyotai.education.common.TenantEntity;
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

/** Subject line of a progress card. */
@Entity
@Table(name = "progress_card_items")
@Getter
@Setter
@NoArgsConstructor
public class ProgressCardItem extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "progress_card_id", nullable = false)
    private ProgressCard progressCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    /** Name frozen at generation time, so renaming a subject later does not alter old cards. */
    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName;

    @Column(name = "test_average", precision = 5, scale = 2)
    private BigDecimal testAverage;

    @Column(name = "exam_average", precision = 5, scale = 2)
    private BigDecimal examAverage;

    @Column(name = "overall_percentage", precision = 5, scale = 2)
    private BigDecimal overallPercentage;

    @Column(length = 10)
    private String grade;

    @Column(length = 500)
    private String remarks;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
