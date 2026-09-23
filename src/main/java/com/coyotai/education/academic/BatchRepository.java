package com.coyotai.education.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    @Query("select b.id from Batch b where b.mentor.id = :mentorId")
    List<Long> findIdsByMentorId(@Param("mentorId") Long mentorId);

    @Query("""
            select b from Batch b
            join fetch b.course c
            join fetch b.academicYear y
            left join fetch b.mentor m
            where (:courseId is null or c.id = :courseId)
              and (:academicYearId is null or y.id = :academicYearId)
              and (:status is null or b.status = :status)
              and (:allBatches = true or b.id in :batchIds)
            order by y.startDate desc, c.displayOrder, b.name
            """)
    List<Batch> search(@Param("courseId") Long courseId,
                       @Param("academicYearId") Long academicYearId,
                       @Param("status") Batch.Status status,
                       @Param("allBatches") boolean allBatches,
                       @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select b from Batch b
            join fetch b.course join fetch b.academicYear left join fetch b.mentor
            where b.id = :id
            """)
    Optional<Batch> findDetail(@Param("id") Long id);

    boolean existsByAcademicYearIdAndNameIgnoreCase(Long academicYearId, String name);

    long countByStatus(Batch.Status status);
}
