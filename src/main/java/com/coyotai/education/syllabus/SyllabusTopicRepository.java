package com.coyotai.education.syllabus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SyllabusTopicRepository extends JpaRepository<SyllabusTopic, Long> {

    @Query("""
            select t from SyllabusTopic t join fetch t.course c join fetch t.subject s
            where (:courseId is null or c.id = :courseId)
              and (:subjectId is null or s.id = :subjectId)
              and (:activeOnly = false or t.active = true)
            order by s.displayOrder, s.name, t.sequenceNo
            """)
    List<SyllabusTopic> search(@Param("courseId") Long courseId, @Param("subjectId") Long subjectId,
                               @Param("activeOnly") boolean activeOnly);
}
