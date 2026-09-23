package com.coyotai.education.parentmeeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ParentMeetingRepository extends JpaRepository<ParentMeeting, Long> {

    @Query("""
            select m from ParentMeeting m
            join fetch m.student s left join fetch m.mentor
            where (:studentId is null or s.id = :studentId)
              and (:status is null or m.status = :status)
              and m.meetingDate between :from and :to
              and (:allBatches = true or s.batch.id in :batchIds)
            order by m.meetingDate desc, m.id desc
            """)
    List<ParentMeeting> search(@Param("studentId") Long studentId,
                               @Param("status") ParentMeeting.Status status,
                               @Param("from") LocalDate from, @Param("to") LocalDate to,
                               @Param("allBatches") boolean allBatches,
                               @Param("batchIds") Collection<Long> batchIds);
}
