package com.coyotai.education.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, Long> {

    /** [batchId, subjectId] pairs the faculty member is scheduled to teach. */
    @Query("select distinct s.batch.id, s.subject.id from ClassSchedule s where s.faculty.id = :facultyId")
    List<Object[]> findBatchSubjectPairs(@Param("facultyId") Long facultyId);

    @Query("""
            select s from ClassSchedule s
            join fetch s.batch b join fetch s.subject sub join fetch s.course
            left join fetch s.faculty f
            where s.scheduleDate between :from and :to
              and (:batchId is null or b.id = :batchId)
              and (:facultyId is null or f.id = :facultyId)
              and (:allBatches = true or b.id in :batchIds)
            order by s.scheduleDate, s.startTime, b.name
            """)
    List<ClassSchedule> search(@Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("batchId") Long batchId,
                               @Param("facultyId") Long facultyId,
                               @Param("allBatches") boolean allBatches,
                               @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select s from ClassSchedule s
            join fetch s.batch join fetch s.subject join fetch s.course left join fetch s.faculty
            where s.id = :id
            """)
    Optional<ClassSchedule> findDetail(@Param("id") Long id);

    /** Classes that overlap [start, end) on the date for the faculty member, batch or room. */
    @Query("""
            select s from ClassSchedule s
            join fetch s.batch join fetch s.subject left join fetch s.faculty
            where s.scheduleDate = :date
              and s.status <> com.coyotai.education.schedule.ClassSchedule.Status.CANCELLED
              and s.startTime < :endTime and s.endTime > :startTime
              and (:excludeId is null or s.id <> :excludeId)
              and ((:facultyId is not null and s.faculty.id = :facultyId)
                   or s.batch.id = :batchId
                   or (:room is not null and s.room = :room))
            """)
    List<ClassSchedule> findConflicts(@Param("date") LocalDate date,
                                      @Param("startTime") LocalTime startTime,
                                      @Param("endTime") LocalTime endTime,
                                      @Param("facultyId") Long facultyId,
                                      @Param("batchId") Long batchId,
                                      @Param("room") String room,
                                      @Param("excludeId") Long excludeId);

    long countByScheduleDateAndStatusNot(LocalDate date, ClassSchedule.Status status);
}
