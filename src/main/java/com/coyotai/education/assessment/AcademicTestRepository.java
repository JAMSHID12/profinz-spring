package com.coyotai.education.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AcademicTestRepository extends JpaRepository<AcademicTest, Long> {

    @Query("""
            select t from AcademicTest t join fetch t.batch b join fetch t.subject s
            where (:type is null or t.testType = :type)
              and (:batchId is null or b.id = :batchId)
              and (:subjectId is null or s.id = :subjectId)
              and (:status is null or t.status = :status)
              and t.testDate between :from and :to
              and (:allBatches = true or b.id in :batchIds)
            order by t.testDate desc, t.id desc
            """)
    List<AcademicTest> search(@Param("type") AcademicTest.Type type,
                              @Param("batchId") Long batchId,
                              @Param("subjectId") Long subjectId,
                              @Param("status") PublicationStatus status,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("allBatches") boolean allBatches,
                              @Param("batchIds") Collection<Long> batchIds);

    @Query("select t from AcademicTest t join fetch t.batch b join fetch b.course join fetch t.subject where t.id = :id")
    Optional<AcademicTest> findDetail(@Param("id") Long id);
}
