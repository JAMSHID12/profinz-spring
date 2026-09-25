-- Preserve historical reason values for reference; they do not prove prior notice.
ALTER TABLE attendance ADD COLUMN legacy_absence_reason VARCHAR(20) NULL;
UPDATE attendance SET legacy_absence_reason = absence_reason
WHERE absence_reason IN ('MEDICAL', 'PERSONAL', 'OTHER');
UPDATE attendance SET absence_reason = 'NOT_INFORMED'
WHERE status IN ('ABSENT', 'EXCUSED') AND
      (absence_reason IS NULL OR absence_reason IN ('MEDICAL', 'PERSONAL', 'OTHER'));
UPDATE attendance SET absence_reason = NULL WHERE status NOT IN ('ABSENT', 'EXCUSED');
