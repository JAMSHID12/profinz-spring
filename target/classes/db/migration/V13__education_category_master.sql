CREATE TABLE education_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_education_categories_code UNIQUE (client_id, code),
    CONSTRAINT fk_education_categories_client FOREIGN KEY (client_id) REFERENCES clients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO education_categories (client_id, code, name, display_order, active, created_at, updated_at)
SELECT id, 'PLUS_TWO', '+2 Completed Students', 1, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) FROM clients;
INSERT INTO education_categories (client_id, code, name, display_order, active, created_at, updated_at)
SELECT id, 'DEGREE', 'Degree Completed Students', 2, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) FROM clients;

ALTER TABLE students ADD COLUMN education_category_id BIGINT NULL,
    ADD CONSTRAINT fk_students_education_category FOREIGN KEY (education_category_id) REFERENCES education_categories(id);
ALTER TABLE syllabus_topics ADD COLUMN education_category_id BIGINT NULL,
    ADD CONSTRAINT fk_topics_education_category FOREIGN KEY (education_category_id) REFERENCES education_categories(id);

UPDATE students s JOIN education_categories c ON c.client_id = s.client_id AND c.code = s.education_category
SET s.education_category_id = c.id;
UPDATE syllabus_topics t JOIN education_categories c ON c.client_id = t.client_id
    AND c.code = CASE t.eligibility WHEN 'PLUS_TWO_ONLY' THEN 'PLUS_TWO' WHEN 'DEGREE_ONLY' THEN 'DEGREE' END
SET t.education_category_id = c.id, t.eligibility = 'CATEGORY_ONLY';
