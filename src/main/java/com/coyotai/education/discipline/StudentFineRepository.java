package com.coyotai.education.discipline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface StudentFineRepository extends JpaRepository<StudentFine, Long> {

    @Query("""
            select f from StudentFine f join fetch f.student s left join fetch s.batch b
            where (:studentId is null or s.id = :studentId)
              and (:status is null or f.status = :status)
              and (:batchId is null or b.id = :batchId)
              and (:allBatches = true or b.id in :batchIds)
            order by f.fineDate desc, f.id desc
            """)
    List<StudentFine> search(@Param("studentId") Long studentId, @Param("status") StudentFine.Status status,
                             @Param("batchId") Long batchId, @Param("allBatches") boolean allBatches,
                             @Param("batchIds") Collection<Long> batchIds);

    List<StudentFine> findAllByStudentIdOrderByFineDateDesc(Long studentId);

    boolean existsByDisciplineRecordId(Long disciplineRecordId);

    @Query("""
            select coalesce(sum(f.amount), 0) from StudentFine f
            where f.student.id = :studentId and f.status = com.coyotai.education.discipline.StudentFine.Status.PENDING
            """)
    BigDecimal sumPendingForStudent(@Param("studentId") Long studentId);

    @Query("""
            select coalesce(sum(f.amount), 0) from StudentFine f
            where f.status = com.coyotai.education.discipline.StudentFine.Status.PENDING
              and (:allBatches = true or f.student.batch.id in :batchIds)
            """)
    BigDecimal sumPending(@Param("allBatches") boolean allBatches, @Param("batchIds") Collection<Long> batchIds);
}
