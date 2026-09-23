package com.coyotai.education.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    @Query("""
            select e from Exam e
            join fetch e.examType join fetch e.course join fetch e.batch b join fetch e.subject s
            left join fetch e.faculty
            where (:batchId is null or b.id = :batchId)
              and (:subjectId is null or s.id = :subjectId)
              and (:status is null or e.status = :status)
              and e.examDate between :from and :to
              and (:allBatches = true or b.id in :batchIds)
            order by e.examDate desc, e.id desc
            """)
    List<Exam> search(@Param("batchId") Long batchId,
                      @Param("subjectId") Long subjectId,
                      @Param("status") PublicationStatus status,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      @Param("allBatches") boolean allBatches,
                      @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select e from Exam e
            join fetch e.examType join fetch e.course join fetch e.batch join fetch e.subject left join fetch e.faculty
            where e.id = :id
            """)
    Optional<Exam> findDetail(@Param("id") Long id);

    @Query("""
            select e from Exam e join fetch e.batch b join fetch e.subject join fetch e.examType
            where e.examDate between :from and :to and (:allBatches = true or b.id in :batchIds)
            order by e.examDate, e.startTime
            """)
    List<Exam> upcoming(@Param("from") LocalDate from, @Param("to") LocalDate to,
                        @Param("allBatches") boolean allBatches, @Param("batchIds") Collection<Long> batchIds);
}
