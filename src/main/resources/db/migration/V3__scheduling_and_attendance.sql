-- =============================================================================
-- V3: class schedule, class register, faculty entry/exit and attendance.
-- =============================================================================

CREATE TABLE class_schedules (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    client_id     BIGINT       NOT NULL,
    batch_id      BIGINT       NOT NULL,
    course_id     BIGINT       NOT NULL,
    subject_id    BIGINT       NOT NULL,
    faculty_id    BIGINT       NULL,
    schedule_date DATE         NOT NULL,
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    room          VARCHAR(60)  NULL,
    status        VARCHAR(20)  NOT NULL,
    notes         VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_class_schedules_times CHECK (start_time < end_time),
    KEY idx_class_schedules_date (client_id, schedule_date),
    KEY idx_class_schedules_batch_date (batch_id, schedule_date),
    KEY idx_class_schedules_faculty_date (faculty_id, schedule_date),
    CONSTRAINT fk_class_schedules_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_class_schedules_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_class_schedules_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_class_schedules_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_class_schedules_faculty FOREIGN KEY (faculty_id) REFERENCES faculty (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- What actually happened in a scheduled class. Basis for future faculty payments.
CREATE TABLE class_register_entries (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    client_id         BIGINT       NOT NULL,
    class_schedule_id BIGINT       NOT NULL,
    faculty_id        BIGINT       NULL,
    actual_start      TIME         NOT NULL,
    actual_end        TIME         NOT NULL,
    topic_covered     VARCHAR(500) NOT NULL,
    student_count     INT          NULL,
    remarks           VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT       NULL,
    updated_by        BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_class_register_schedule UNIQUE (class_schedule_id),
    CONSTRAINT chk_class_register_times CHECK (actual_start < actual_end),
    CONSTRAINT chk_class_register_count CHECK (student_count IS NULL OR student_count >= 0),
    CONSTRAINT fk_class_register_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_class_register_schedule FOREIGN KEY (class_schedule_id) REFERENCES class_schedules (id),
    CONSTRAINT fk_class_register_faculty FOREIGN KEY (faculty_id) REFERENCES faculty (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE faculty_entry_exit (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    client_id     BIGINT       NOT NULL,
    faculty_id    BIGINT       NOT NULL,
    entry_date    DATE         NOT NULL,
    session_label VARCHAR(40)  NOT NULL,
    entry_time    TIME         NOT NULL,
    exit_time     TIME         NULL,
    remarks       VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_faculty_entry_exit UNIQUE (faculty_id, entry_date, session_label),
    CONSTRAINT chk_faculty_entry_exit_times CHECK (exit_time IS NULL OR entry_time < exit_time),
    CONSTRAINT fk_faculty_entry_exit_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_faculty_entry_exit_faculty FOREIGN KEY (faculty_id) REFERENCES faculty (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- session_key is the class schedule id, or 0 for whole-day attendance. It makes the
-- "one record per student, date and session" rule enforceable by a plain unique key.
CREATE TABLE attendance (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    client_id           BIGINT       NOT NULL,
    student_id          BIGINT       NOT NULL,
    batch_id            BIGINT       NOT NULL,
    class_schedule_id   BIGINT       NULL,
    session_key         BIGINT       NOT NULL DEFAULT 0,
    attendance_date     DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    remarks             VARCHAR(255) NULL,
    marked_by           BIGINT       NULL,
    marked_at           DATETIME(6)  NOT NULL,
    notification_status VARCHAR(20)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT       NULL,
    updated_by          BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attendance_student_date_session UNIQUE (student_id, attendance_date, session_key),
    KEY idx_attendance_student_date (student_id, attendance_date),
    KEY idx_attendance_batch_date (batch_id, attendance_date),
    KEY idx_attendance_schedule (class_schedule_id),
    CONSTRAINT fk_attendance_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_attendance_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_attendance_schedule FOREIGN KEY (class_schedule_id) REFERENCES class_schedules (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
