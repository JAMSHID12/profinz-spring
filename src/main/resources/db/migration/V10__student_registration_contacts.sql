-- Student-owned contact details and immutable contact snapshots on historical records.
ALTER TABLE students
    ADD COLUMN parent_name VARCHAR(150) NULL,
    ADD COLUMN parent_relation VARCHAR(30) NULL,
    ADD COLUMN parent_phone_number VARCHAR(20) NULL,
    ADD COLUMN parent_whatsapp_number VARCHAR(20) NULL,
    ADD COLUMN parent_email VARCHAR(150) NULL,
    ADD COLUMN parent_whatsapp_opt_in BOOLEAN NULL,
    ADD COLUMN parent_active BOOLEAN NULL;
UPDATE students t JOIN parents p ON t.parent_id = p.id AND t.client_id = p.client_id
SET t.parent_name=p.name, t.parent_relation=p.relation, t.parent_phone_number=p.phone_number,
    t.parent_whatsapp_number=p.whatsapp_number, t.parent_email=p.email,
    t.parent_whatsapp_opt_in=p.whatsapp_opt_in, t.parent_active=p.active;
ALTER TABLE students DROP FOREIGN KEY fk_students_parent, DROP COLUMN parent_id;

ALTER TABLE parent_meetings
    ADD COLUMN parent_name VARCHAR(150) NULL,
    ADD COLUMN parent_relation VARCHAR(30) NULL,
    ADD COLUMN parent_phone_number VARCHAR(20) NULL,
    ADD COLUMN parent_whatsapp_number VARCHAR(20) NULL,
    ADD COLUMN parent_email VARCHAR(150) NULL,
    ADD COLUMN parent_whatsapp_opt_in BOOLEAN NULL,
    ADD COLUMN parent_active BOOLEAN NULL;
UPDATE parent_meetings t JOIN parents p ON t.parent_id = p.id AND t.client_id = p.client_id
SET t.parent_name=p.name, t.parent_relation=p.relation, t.parent_phone_number=p.phone_number,
    t.parent_whatsapp_number=p.whatsapp_number, t.parent_email=p.email,
    t.parent_whatsapp_opt_in=p.whatsapp_opt_in, t.parent_active=p.active;
ALTER TABLE parent_meetings DROP FOREIGN KEY fk_parent_meetings_parent, DROP COLUMN parent_id;

ALTER TABLE notifications
    ADD COLUMN parent_name VARCHAR(150) NULL,
    ADD COLUMN parent_relation VARCHAR(30) NULL,
    ADD COLUMN parent_phone_number VARCHAR(20) NULL,
    ADD COLUMN parent_whatsapp_number VARCHAR(20) NULL,
    ADD COLUMN parent_email VARCHAR(150) NULL,
    ADD COLUMN parent_whatsapp_opt_in BOOLEAN NULL,
    ADD COLUMN parent_active BOOLEAN NULL;
UPDATE notifications t JOIN parents p ON t.parent_id = p.id AND t.client_id = p.client_id
SET t.parent_name=p.name, t.parent_relation=p.relation, t.parent_phone_number=p.phone_number,
    t.parent_whatsapp_number=p.whatsapp_number, t.parent_email=p.email,
    t.parent_whatsapp_opt_in=p.whatsapp_opt_in, t.parent_active=p.active;
ALTER TABLE notifications DROP FOREIGN KEY fk_notifications_parent, DROP COLUMN parent_id;

DROP TABLE parents;
ALTER TABLE students ADD COLUMN photo_file VARCHAR(100) NULL;
CREATE TABLE student_identifier_sequences (
    client_id BIGINT NOT NULL,
    sequence_key VARCHAR(80) NOT NULL,
    next_value BIGINT NOT NULL,
    PRIMARY KEY (client_id, sequence_key),
    CONSTRAINT fk_student_sequence_client FOREIGN KEY (client_id) REFERENCES clients(id)
) ENGINE=InnoDB;
DELETE rp FROM role_permissions rp JOIN permissions p ON rp.permission_id=p.id
WHERE p.code IN ('PARENT_VIEW','PARENT_MANAGE');
