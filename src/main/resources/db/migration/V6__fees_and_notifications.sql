-- =============================================================================
-- V6: fee plans, installments, payments (receipts) and the notification queue.
-- =============================================================================

-- A fee plan: what a student owes for a course / academic year.
CREATE TABLE student_fees (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    client_id        BIGINT        NOT NULL,
    student_id       BIGINT        NOT NULL,
    course_id        BIGINT        NULL,
    academic_year_id BIGINT        NULL,
    title            VARCHAR(150)  NOT NULL,
    total_amount     DECIMAL(12,2) NOT NULL,
    discount_amount  DECIMAL(12,2) NOT NULL DEFAULT 0,
    net_amount       DECIMAL(12,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    notes            VARCHAR(500)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    created_by       BIGINT        NULL,
    updated_by       BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_student_fees_amounts CHECK (total_amount >= 0 AND discount_amount >= 0 AND net_amount >= 0),
    KEY idx_student_fees_student (student_id),
    CONSTRAINT fk_student_fees_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_student_fees_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_student_fees_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_student_fees_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE fee_installments (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    client_id          BIGINT        NOT NULL,
    student_fee_id     BIGINT        NOT NULL,
    student_id         BIGINT        NOT NULL,
    installment_no     INT           NOT NULL,
    label              VARCHAR(100)  NOT NULL,
    due_date           DATE          NOT NULL,
    amount             DECIMAL(12,2) NOT NULL,
    paid_amount        DECIMAL(12,2) NOT NULL DEFAULT 0,
    pending_amount     DECIMAL(12,2) NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    reminder_count     INT           NOT NULL DEFAULT 0,
    last_reminder_date DATE          NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    created_by         BIGINT        NULL,
    updated_by         BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fee_installments_plan_no UNIQUE (student_fee_id, installment_no),
    CONSTRAINT chk_fee_installments_amounts CHECK (amount >= 0 AND paid_amount >= 0 AND pending_amount >= 0),
    KEY idx_fee_installments_student (student_id, status),
    KEY idx_fee_installments_due (client_id, due_date),
    KEY idx_fee_installments_status (client_id, status),
    CONSTRAINT fk_fee_installments_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_fee_installments_plan FOREIGN KEY (student_fee_id) REFERENCES student_fees (id),
    CONSTRAINT fk_fee_installments_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Each payment is also its receipt (receipt_number is unique per client).
CREATE TABLE payments (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    client_id        BIGINT        NOT NULL,
    installment_id   BIGINT        NOT NULL,
    student_id       BIGINT        NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    payment_date     DATE          NOT NULL,
    payment_method   VARCHAR(30)   NOT NULL,
    reference_number VARCHAR(60)   NULL,
    receipt_number   VARCHAR(60)   NOT NULL,
    notes            VARCHAR(255)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    created_by       BIGINT        NULL,
    updated_by       BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_client_receipt UNIQUE (client_id, receipt_number),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    KEY idx_payments_student_date (student_id, payment_date),
    KEY idx_payments_installment (installment_id),
    CONSTRAINT fk_payments_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_payments_installment FOREIGN KEY (installment_id) REFERENCES fee_installments (id),
    CONSTRAINT fk_payments_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Outbound messages on any channel. Business transactions only insert rows here;
-- the scheduler sends them later, outside those transactions.
CREATE TABLE notifications (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    client_id         BIGINT       NOT NULL,
    event_type        VARCHAR(40)  NOT NULL,
    channel           VARCHAR(20)  NOT NULL,
    recipient_type    VARCHAR(20)  NOT NULL,
    recipient_user_id BIGINT       NULL,
    student_id        BIGINT       NULL,
    parent_id         BIGINT       NULL,
    destination       VARCHAR(150) NULL,
    template_name     VARCHAR(100) NULL,
    title             VARCHAR(200) NULL,
    message_payload   TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    retry_count       INT          NOT NULL DEFAULT 0,
    error_message     VARCHAR(500) NULL,
    scheduled_at      DATETIME(6)  NOT NULL,
    sent_at           DATETIME(6)  NULL,
    read_at           DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_status (status),
    KEY idx_notifications_scheduled_at (scheduled_at),
    KEY idx_notifications_queue (client_id, status, scheduled_at),
    KEY idx_notifications_recipient (recipient_user_id, read_at),
    KEY idx_notifications_student (student_id),
    KEY idx_notifications_created_at (client_id, created_at),
    CONSTRAINT fk_notifications_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_notifications_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_notifications_parent FOREIGN KEY (parent_id) REFERENCES parents (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
