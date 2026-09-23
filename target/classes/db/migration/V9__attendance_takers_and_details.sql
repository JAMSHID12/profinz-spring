-- =============================================================================
-- V9: attendance is taken by mentors and faculty only, and a mark carries details.
--
-- 1. Status LEAVE is renamed EXCUSED (same meaning: an allowed absence, no parent message).
-- 2. A mark can record how late a student was, why they were absent or excused, and what
--    was noticed while taking attendance (no uniform, no ID tag).
-- 3. Those observations become discipline records linked to the mark, so the discipline
--    history and progress cards include them - once, however often the sheet is saved.
-- 4. Students can have a photo, shown on the attendance sheet.
-- 5. Only MENTORS and FACULTY may take or correct attendance. Every other role keeps (at
--    most) read access. The application enforces the same rule in code.
-- =============================================================================

UPDATE attendance SET status = 'EXCUSED' WHERE status = 'LEAVE';

ALTER TABLE attendance
    ADD COLUMN late_minutes   INT         NULL AFTER status,
    ADD COLUMN absence_reason VARCHAR(20) NULL AFTER late_minutes,
    ADD COLUMN no_uniform     BOOLEAN     NOT NULL DEFAULT FALSE AFTER absence_reason,
    ADD COLUMN no_id_tag      BOOLEAN     NOT NULL DEFAULT FALSE AFTER no_uniform,
    ADD CONSTRAINT chk_attendance_late_minutes CHECK (late_minutes IS NULL OR late_minutes BETWEEN 1 AND 600);

ALTER TABLE discipline_records
    ADD COLUMN attendance_id BIGINT NULL AFTER batch_id,
    ADD CONSTRAINT uk_discipline_records_attendance_type UNIQUE (attendance_id, discipline_type_id),
    ADD CONSTRAINT fk_discipline_records_attendance FOREIGN KEY (attendance_id) REFERENCES attendance (id);

ALTER TABLE students
    ADD COLUMN photo_url VARCHAR(500) NULL AFTER address;

-- Taking and correcting attendance: mentors and faculty only.
DELETE rp FROM role_permissions rp
    JOIN roles r ON r.id = rp.role_id
    JOIN permissions p ON p.id = rp.permission_id
WHERE p.code IN ('ATTENDANCE_CREATE', 'ATTENDANCE_UPDATE')
  AND r.code NOT IN ('MENTORS', 'FACULTY');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code IN ('MENTORS', 'FACULTY')
  AND p.code IN ('ATTENDANCE_VIEW', 'ATTENDANCE_CREATE', 'ATTENDANCE_UPDATE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

UPDATE permissions SET name = 'Take attendance (mentors and faculty only)' WHERE code = 'ATTENDANCE_CREATE';
UPDATE permissions SET name = 'Correct attendance (mentors and faculty only)' WHERE code = 'ATTENDANCE_UPDATE';
