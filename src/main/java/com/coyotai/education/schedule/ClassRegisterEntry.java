package com.coyotai.education.schedule;

import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.staff.Faculty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** What was actually taught in a scheduled class. Future basis for faculty payments. */
@Entity
@Table(name = "class_register_entries")
@Getter
@Setter
@NoArgsConstructor
public class ClassRegisterEntry extends AuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_schedule_id", nullable = false)
    private ClassSchedule classSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @Column(name = "actual_start", nullable = false)
    private LocalTime actualStart;

    @Column(name = "actual_end", nullable = false)
    private LocalTime actualEnd;

    @Column(name = "topic_covered", nullable = false, length = 500)
    private String topicCovered;

    @Column(name = "student_count")
    private Integer studentCount;

    @Column(length = 500)
    private String remarks;
}
