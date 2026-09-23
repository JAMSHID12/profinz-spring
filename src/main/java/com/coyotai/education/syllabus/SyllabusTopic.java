package com.coyotai.education.syllabus;

import com.coyotai.education.academic.Course;
import com.coyotai.education.academic.Subject;
import com.coyotai.education.common.AuditedEntity;
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

/** A topic of a subject's syllabus, in teaching order. */
@Entity
@Table(name = "syllabus_topics")
@Getter
@Setter
@NoArgsConstructor
public class SyllabusTopic extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "planned_hours", precision = 5, scale = 1)
    private BigDecimal plannedHours;

    @Column(nullable = false)
    private boolean active = true;
}
