package com.coyotai.education.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FacultyAssignmentRepository extends JpaRepository<FacultyAssignment, Long> {

    /** [batchId, subjectId] pairs a faculty member is actively assigned to. */
    @Query("select a.batch.id, a.subject.id from FacultyAssignment a where a.faculty.id = :facultyId and a.active = true")
    List<Object[]> findActivePairs(@Param("facultyId") Long facultyId);

    @Query("""
            select a from FacultyAssignment a
            join fetch a.faculty f join fetch a.batch b join fetch a.subject s
            where (:facultyId is null or f.id = :facultyId)
              and (:batchId is null or b.id = :batchId)
            order by f.fullName, b.name, s.name
            """)
    List<FacultyAssignment> search(@Param("facultyId") Long facultyId, @Param("batchId") Long batchId);

    boolean existsByFacultyIdAndBatchIdAndSubjectId(Long facultyId, Long batchId, Long subjectId);
}
