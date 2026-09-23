-- =============================================================================
-- V8: course fees in master data and per-student discounts.
--
-- Every course has a standard fee (and a default number of installments). A fee
-- plan copies the course fee when it is created, and each student can get their
-- own discount with a recorded reason. Changing a course fee later affects new
-- plans only; existing plans keep the fee they were created with.
--
-- Additive only: new nullable columns, existing rows keep working unchanged.
-- =============================================================================

ALTER TABLE courses
    ADD COLUMN fee_amount DECIMAL(12,2) NULL AFTER description,
    ADD COLUMN default_installments INT NULL AFTER fee_amount,
    ADD CONSTRAINT chk_courses_fee CHECK (fee_amount IS NULL OR fee_amount >= 0),
    ADD CONSTRAINT chk_courses_default_installments
        CHECK (default_installments IS NULL OR default_installments BETWEEN 1 AND 36);

ALTER TABLE student_fees
    ADD COLUMN discount_reason VARCHAR(255) NULL AFTER discount_amount,
    ADD CONSTRAINT chk_student_fees_discount CHECK (discount_amount <= total_amount);
