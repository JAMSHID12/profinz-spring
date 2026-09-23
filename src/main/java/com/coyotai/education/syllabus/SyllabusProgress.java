package com.coyotai.education.syllabus;

import com.coyotai.education.academic.Batch;
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

import java.time.LocalDate;

/** How far one batch has got with one syllabus topic. */
@Entity
@Table(name = "syllabus_progress")
@Getter
@Setter
@NoArgsConstructor
public class SyllabusProgress extends AuditedEntity {

    public enum Status { NOT_STARTED, IN_PROGRESS, COMPLETED, DELAYED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private SyllabusTopic topic;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.NOT_STARTED;

    @Column(length = 500)
    private String remarks;
}
