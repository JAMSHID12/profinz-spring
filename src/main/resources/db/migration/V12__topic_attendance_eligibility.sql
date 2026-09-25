ALTER TABLE students ADD COLUMN education_category VARCHAR(20) NULL;
ALTER TABLE syllabus_topics ADD COLUMN eligibility VARCHAR(20) NOT NULL DEFAULT 'BOTH';
ALTER TABLE class_schedules ADD COLUMN topic_id BIGINT NULL,
    ADD CONSTRAINT fk_class_schedules_topic FOREIGN KEY (topic_id) REFERENCES syllabus_topics(id);
