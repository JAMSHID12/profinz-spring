package com.coyotai.education.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentBatchAssignmentRepository extends JpaRepository<StudentBatchAssignment, Long> {

    @Query("""
            select a from StudentBatchAssignment a
            join fetch a.batch join fetch a.course join fetch a.academicYear
            where a.student.id = :studentId
            order by a.startDate desc, a.id desc
            """)
    List<StudentBatchAssignment> findHistory(@Param("studentId") Long studentId);

    Optional<StudentBatchAssignment> findFirstByStudentIdAndStatus(Long studentId, StudentBatchAssignment.Status status);
}
