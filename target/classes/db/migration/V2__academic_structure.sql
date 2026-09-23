-- =============================================================================
-- V2: academic structure - courses, subjects, academic years, staff profiles,
-- batches, parents, students and batch assignment history, plus the per-client
-- master data lists (discipline types, exam types).
--
-- Nothing here is client-specific data: courses such as CA or ACCA are rows
-- created per client, never values in code.
-- =============================================================================

CREATE TABLE courses (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    client_id     BIGINT       NOT NULL,
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_courses_client_code UNIQUE (client_id, code),
    KEY idx_courses_client_status (client_id, status),
    CONSTRAINT fk_courses_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE subjects (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    client_id     BIGINT       NOT NULL,
    course_id     BIGINT       NOT NULL,
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    display_order INT          NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subjects_course_code UNIQUE (course_id, code),
    CONSTRAINT fk_subjects_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_subjects_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE academic_years (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    client_id  BIGINT      NOT NULL,
    name       VARCHAR(30) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    is_current BOOLEAN     NOT NULL DEFAULT FALSE,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_years_client_name UNIQUE (client_id, name),
    CONSTRAINT chk_academic_years_dates CHECK (start_date < end_date),
    CONSTRAINT fk_academic_years_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE mentors (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    client_id      BIGINT       NOT NULL,
    user_id        BIGINT       NULL,
    employee_code  VARCHAR(30)  NULL,
    full_name      VARCHAR(150) NOT NULL,
    mobile         VARCHAR(20)  NULL,
    email          VARCHAR(150) NULL,
    specialization VARCHAR(150) NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    created_by     BIGINT       NULL,
    updated_by     BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mentors_user UNIQUE (user_id),
    CONSTRAINT uk_mentors_client_employee_code UNIQUE (client_id, employee_code),
    CONSTRAINT fk_mentors_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_mentors_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE faculty (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    client_id      BIGINT       NOT NULL,
    user_id        BIGINT       NULL,
    employee_code  VARCHAR(30)  NULL,
    full_name      VARCHAR(150) NOT NULL,
    mobile         VARCHAR(20)  NULL,
    email          VARCHAR(150) NULL,
    faculty_type   VARCHAR(20)  NOT NULL,
    specialization VARCHAR(150) NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    created_by     BIGINT       NULL,
    updated_by     BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_faculty_user UNIQUE (user_id),
    CONSTRAINT uk_faculty_client_employee_code UNIQUE (client_id, employee_code),
    CONSTRAINT fk_faculty_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_faculty_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE batches (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    client_id        BIGINT       NOT NULL,
    name             VARCHAR(120) NOT NULL,
    course_id        BIGINT       NOT NULL,
    academic_year_id BIGINT       NOT NULL,
    mentor_id        BIGINT       NULL,
    start_date       DATE         NULL,
    end_date         DATE         NULL,
    capacity         INT          NULL,
    status           VARCHAR(20)  NOT NULL,
    description      VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NULL,
    updated_by       BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_batches_client_year_name UNIQUE (client_id, academic_year_id, name),
    CONSTRAINT chk_batches_capacity CHECK (capacity IS NULL OR capacity > 0),
    KEY idx_batches_mentor (mentor_id),
    KEY idx_batches_course (course_id),
    CONSTRAINT fk_batches_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_batches_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_batches_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_batches_mentor FOREIGN KEY (mentor_id) REFERENCES mentors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Which faculty teaches which subject to which batch. Defines faculty data scope.
CREATE TABLE faculty_assignments (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    client_id  BIGINT      NOT NULL,
    faculty_id BIGINT      NOT NULL,
    batch_id   BIGINT      NOT NULL,
    subject_id BIGINT      NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_faculty_assignments UNIQUE (faculty_id, batch_id, subject_id),
    CONSTRAINT fk_faculty_assignments_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_faculty_assignments_faculty FOREIGN KEY (faculty_id) REFERENCES faculty (id),
    CONSTRAINT fk_faculty_assignments_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_faculty_assignments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE parents (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    client_id       BIGINT       NOT NULL,
    name            VARCHAR(150) NOT NULL,
    relation        VARCHAR(30)  NULL,
    phone_number    VARCHAR(20)  NOT NULL,
    whatsapp_number VARCHAR(20)  NULL,
    email           VARCHAR(150) NULL,
    whatsapp_opt_in BOOLEAN      NOT NULL DEFAULT TRUE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_parents_client_phone (client_id, phone_number),
    KEY idx_parents_client_name (client_id, name),
    CONSTRAINT fk_parents_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE students (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    client_id        BIGINT       NOT NULL,
    user_id          BIGINT       NULL,
    student_code     VARCHAR(30)  NOT NULL,
    admission_number VARCHAR(40)  NOT NULL,
    full_name        VARCHAR(150) NOT NULL,
    date_of_birth    DATE         NULL,
    gender           VARCHAR(20)  NULL,
    mobile           VARCHAR(20)  NULL,
    email            VARCHAR(150) NULL,
    address          VARCHAR(500) NULL,
    parent_id        BIGINT       NULL,
    course_id        BIGINT       NULL,
    batch_id         BIGINT       NULL,
    academic_year_id BIGINT       NULL,
    admission_date   DATE         NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NULL,
    updated_by       BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_students_user UNIQUE (user_id),
    CONSTRAINT uk_students_client_code UNIQUE (client_id, student_code),
    CONSTRAINT uk_students_client_admission UNIQUE (client_id, admission_number),
    KEY idx_students_batch (batch_id),
    KEY idx_students_parent (parent_id),
    KEY idx_students_client_name (client_id, full_name),
    CONSTRAINT fk_students_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_students_parent FOREIGN KEY (parent_id) REFERENCES parents (id),
    CONSTRAINT fk_students_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_students_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_students_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Batch history is never overwritten: a transfer closes one row and opens another.
CREATE TABLE student_batch_assignments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    client_id        BIGINT       NOT NULL,
    student_id       BIGINT       NOT NULL,
    batch_id         BIGINT       NOT NULL,
    course_id        BIGINT       NOT NULL,
    academic_year_id BIGINT       NOT NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NULL,
    status           VARCHAR(20)  NOT NULL,
    reason           VARCHAR(255) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NULL,
    updated_by       BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_student_batch_assignments_student (student_id, status),
    KEY idx_student_batch_assignments_batch (batch_id, status),
    CONSTRAINT fk_sba_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_sba_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_sba_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_sba_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_sba_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE discipline_types (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    client_id           BIGINT        NOT NULL,
    code                VARCHAR(30)   NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    default_fine_amount DECIMAL(12,2) NULL,
    display_order       INT           NOT NULL DEFAULT 0,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    created_by          BIGINT        NULL,
    updated_by          BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_discipline_types_client_code UNIQUE (client_id, code),
    CONSTRAINT chk_discipline_types_fine CHECK (default_fine_amount IS NULL OR default_fine_amount >= 0),
    CONSTRAINT fk_discipline_types_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE exam_types (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    client_id     BIGINT       NOT NULL,
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_types_client_code UNIQUE (client_id, code),
    CONSTRAINT fk_exam_types_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
