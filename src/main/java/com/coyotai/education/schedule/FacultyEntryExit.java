package com.coyotai.education.schedule;

import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.staff.Faculty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** A faculty member's arrival and departure for one session of a day. */
@Entity
@Table(name = "faculty_entry_exit")
@Getter
@Setter
@NoArgsConstructor
public class FacultyEntryExit extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "session_label", nullable = false, length = 40)
    private String sessionLabel;

    @Column(name = "entry_time", nullable = false)
    private LocalTime entryTime;

    @Column(name = "exit_time")
    private LocalTime exitTime;

    @Column(length = 255)
    private String remarks;
}
