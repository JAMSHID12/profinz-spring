package com.coyotai.education.progress;

import com.coyotai.education.assessment.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProgressCardRepository extends JpaRepository<ProgressCard, Long> {

    @Query("""
            select c from ProgressCard c join fetch c.student s left join fetch c.batch b
            where (:studentId is null or s.id = :studentId)
              and (:batchId is null or b.id = :batchId)
              and (:status is null or c.status = :status)
              and (:allBatches = true or s.batch.id in :batchIds)
            order by c.periodEnd desc, s.fullName
            """)
    List<ProgressCard> search(@Param("studentId") Long studentId, @Param("batchId") Long batchId,
                              @Param("status") PublicationStatus status, @Param("allBatches") boolean allBatches,
                              @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select distinct c from ProgressCard c join fetch c.student s left join fetch s.user
            left join fetch c.batch left join fetch c.items
            where c.id = :id
            """)
    Optional<ProgressCard> findDetail(@Param("id") Long id);

    @Query("""
            select c from ProgressCard c
            where c.student.id = :studentId and c.status = com.coyotai.education.assessment.PublicationStatus.PUBLISHED
            order by c.periodEnd desc
            """)
    List<ProgressCard> findPublishedForStudent(@Param("studentId") Long studentId);

    long countByStatus(PublicationStatus status);
}
