package com.coyotai.education.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentFeeRepository extends JpaRepository<StudentFee, Long> {

    @Query("""
            select distinct f from StudentFee f
            join fetch f.student s left join fetch f.course left join fetch f.academicYear
            left join fetch f.installments
            where (:studentId is null or s.id = :studentId)
            order by f.id desc
            """)
    List<StudentFee> search(@Param("studentId") Long studentId);

    @Query("""
            select f from StudentFee f
            join fetch f.student s left join fetch f.course left join fetch f.academicYear
            left join fetch f.installments
            where f.id = :id
            """)
    Optional<StudentFee> findDetail(@Param("id") Long id);

    /** A student has at most one active plan per course and academic year. */
    @Query("""
            select count(f) > 0 from StudentFee f
            where f.student.id = :studentId and f.course.id = :courseId and f.status = :status
              and ((:yearId is null and f.academicYear is null) or f.academicYear.id = :yearId)
            """)
    boolean existsForCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId,
                            @Param("yearId") Long yearId, @Param("status") StudentFee.Status status);
}
