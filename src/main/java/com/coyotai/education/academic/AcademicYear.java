package com.coyotai.education.academic;

import com.coyotai.education.common.AuditedEntity;
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

import java.time.LocalDate;

/** e.g. 2026-2027. Creating a new year never touches the data of earlier years. */
@Entity
@Table(name = "academic_years")
@Getter
@Setter
@NoArgsConstructor
public class AcademicYear extends AuditedEntity {

    public enum Status { PLANNED, ACTIVE, CLOSED }

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.PLANNED;
}
