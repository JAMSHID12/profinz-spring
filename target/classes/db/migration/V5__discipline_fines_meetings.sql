-- =============================================================================
-- V5: discipline records, student fines and parent meetings.
-- =============================================================================

CREATE TABLE discipline_records (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    client_id          BIGINT        NOT NULL,
    student_id         BIGINT        NOT NULL,
    batch_id           BIGINT        NULL,
    discipline_type_id BIGINT        NOT NULL,
    incident_date      DATE          NOT NULL,
    description        VARCHAR(1000) NOT NULL,
    action_taken       VARCHAR(500)  NULL,
    status             VARCHAR(20)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    created_by         BIGINT        NULL,
    updated_by         BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_discipline_records_student (student_id, incident_date),
    KEY idx_discipline_records_batch (batch_id, incident_date),
    CONSTRAINT fk_discipline_records_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_discipline_records_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_discipline_records_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_discipline_records_type FOREIGN KEY (discipline_type_id) REFERENCES discipline_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE student_fines (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    client_id            BIGINT        NOT NULL,
    student_id           BIGINT        NOT NULL,
    discipline_record_id BIGINT        NULL,
    reason               VARCHAR(255)  NOT NULL,
    amount               DECIMAL(12,2) NOT NULL,
    fine_date            DATE          NOT NULL,
    due_date             DATE          NULL,
    status               VARCHAR(20)   NOT NULL,
    paid_date            DATE          NULL,
    payment_reference    VARCHAR(60)   NULL,
    remarks              VARCHAR(500)  NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    created_by           BIGINT        NULL,
    updated_by           BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_student_fines_amount CHECK (amount > 0),
    KEY idx_student_fines_student (student_id, status),
    KEY idx_student_fines_status (client_id, status),
    CONSTRAINT fk_student_fines_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_student_fines_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_student_fines_discipline FOREIGN KEY (discipline_record_id) REFERENCES discipline_records (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE parent_meetings (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    client_id         BIGINT        NOT NULL,
    student_id        BIGINT        NOT NULL,
    parent_id         BIGINT        NULL,
    mentor_id         BIGINT        NULL,
    meeting_date      DATE          NOT NULL,
    discussion        TEXT          NULL,
    academic_issues   VARCHAR(1000) NULL,
    attendance_issues VARCHAR(1000) NULL,
    discipline_issues VARCHAR(1000) NULL,
    action_items      VARCHAR(1000) NULL,
    follow_up_date    DATE          NULL,
    status            VARCHAR(20)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    created_by        BIGINT        NULL,
    updated_by        BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_parent_meetings_student (student_id, meeting_date),
    KEY idx_parent_meetings_mentor (mentor_id, meeting_date),
    CONSTRAINT fk_parent_meetings_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_parent_meetings_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_parent_meetings_parent FOREIGN KEY (parent_id) REFERENCES parents (id),
    CONSTRAINT fk_parent_meetings_mentor FOREIGN KEY (mentor_id) REFERENCES mentors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
