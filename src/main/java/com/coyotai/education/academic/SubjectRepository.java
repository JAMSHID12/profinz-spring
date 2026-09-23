package com.coyotai.education.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    @Query("""
            select s from Subject s join fetch s.course c
            where (:courseId is null or c.id = :courseId)
            order by c.displayOrder, c.name, s.displayOrder, s.name
            """)
    List<Subject> search(@Param("courseId") Long courseId);

    boolean existsByCourseIdAndCodeIgnoreCase(Long courseId, String code);
}
