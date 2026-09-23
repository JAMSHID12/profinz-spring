package com.coyotai.education.discipline;

import com.coyotai.education.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Uniform, late coming, name badge... configurable per client, with an optional default fine. */
@Entity
@Table(name = "discipline_types")
@Getter
@Setter
@NoArgsConstructor
public class DisciplineType extends AuditedEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "default_fine_amount", precision = 12, scale = 2)
    private BigDecimal defaultFineAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;
}
