package com.coyotai.education.syllabus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SyllabusProgressRepository extends JpaRepository<SyllabusProgress, Long> {

    @Query("select p from SyllabusProgress p join fetch p.topic t where p.batch.id = :batchId")
    List<SyllabusProgress> findByBatch(@Param("batchId") Long batchId);

    Optional<SyllabusProgress> findByBatchIdAndTopicId(Long batchId, Long topicId);
}
