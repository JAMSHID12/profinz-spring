package com.coyotai.education.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("""
            select a from Attendance a join fetch a.student s
            where a.batch.id = :batchId and a.attendanceDate = :date and a.sessionKey = :sessionKey
            """)
    List<Attendance> findSheet(@Param("batchId") Long batchId, @Param("date") LocalDate date,
                               @Param("sessionKey") long sessionKey);

    Optional<Attendance> findByStudentIdAndAttendanceDateAndSessionKey(Long studentId, LocalDate date, long sessionKey);

    @Query(value = """
            select a from Attendance a
            join fetch a.student s join fetch a.batch b
            left join fetch a.classSchedule cs left join fetch cs.subject
            where (:studentId is null or s.id = :studentId)
              and (:batchId is null or b.id = :batchId)
              and (:status is null or a.status = :status)
              and a.attendanceDate between :from and :to
              and (:allBatches = true or b.id in :batchIds)
            order by a.attendanceDate desc, s.fullName
            """,
            countQuery = """
            select count(a) from Attendance a
            where (:studentId is null or a.student.id = :studentId)
              and (:batchId is null or a.batch.id = :batchId)
              and (:status is null or a.status = :status)
              and a.attendanceDate between :from and :to
              and (:allBatches = true or a.batch.id in :batchIds)
            """)
    Page<Attendance> history(@Param("studentId") Long studentId,
                             @Param("batchId") Long batchId,
                             @Param("status") AttendanceStatus status,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("allBatches") boolean allBatches,
                             @Param("batchIds") Collection<Long> batchIds,
                             Pageable pageable);

    @Query("""
            select a.status, count(a) from Attendance a
            where a.student.id = :studentId and a.attendanceDate between :from and :to
            group by a.status
            """)
    List<Object[]> countByStatusForStudent(@Param("studentId") Long studentId,
                                           @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select a.status, count(a) from Attendance a
            where a.attendanceDate = :date and (:allBatches = true or a.batch.id in :batchIds)
            group by a.status
            """)
    List<Object[]> countByStatusOnDate(@Param("date") LocalDate date, @Param("allBatches") boolean allBatches,
                                       @Param("batchIds") Collection<Long> batchIds);

    @Query("select distinct a.batch.id from Attendance a where a.attendanceDate = :date")
    List<Long> findMarkedBatchIds(@Param("date") LocalDate date);

    /** [batchId, sessionKey, marks] for the given batches on a date. */
    @Query("""
            select a.batch.id, a.sessionKey, count(a) from Attendance a
            where a.attendanceDate = :date and a.batch.id in :batchIds
            group by a.batch.id, a.sessionKey
            """)
    List<Object[]> countMarks(@Param("date") LocalDate date, @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select a from Attendance a join fetch a.student s join fetch a.batch b
            where a.attendanceDate = :date
              and a.status in (com.coyotai.education.attendance.AttendanceStatus.ABSENT, com.coyotai.education.attendance.AttendanceStatus.LATE)
              and (:allBatches = true or b.id in :batchIds)
            order by b.name, s.fullName
            """)
    List<Attendance> findAbsentOrLateOn(@Param("date") LocalDate date, @Param("allBatches") boolean allBatches,
                                        @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select s.id, s.fullName, s.admissionNumber, b.name, a.status, count(a)
            from Attendance a join a.student s join a.batch b
            where a.attendanceDate between :from and :to
              and (:batchId is null or b.id = :batchId)
            group by s.id, s.fullName, s.admissionNumber, b.name, a.status
            order by s.fullName
            """)
    List<Object[]> reportByStudent(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("batchId") Long batchId);

    // ---- Dashboard charts: counts within a data scope --------------------------------------------

    /** [date, status, count]. */
    @Query("""
            select a.attendanceDate, a.status, count(a) from Attendance a
            where a.attendanceDate between :from and :to
              and (:batchId is null or a.batch.id = :batchId)
              and (:allBatches = true or a.batch.id in :batchIds)
            group by a.attendanceDate, a.status
            """)
    List<Object[]> countByDayAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                       @Param("batchId") Long batchId, @Param("allBatches") boolean allBatches,
                                       @Param("batchIds") Collection<Long> batchIds);

    /** [batchId, batchName, status, count]. */
    @Query("""
            select b.id, b.name, a.status, count(a) from Attendance a join a.batch b
            where a.attendanceDate between :from and :to
              and (:batchId is null or b.id = :batchId)
              and (:allBatches = true or b.id in :batchIds)
            group by b.id, b.name, a.status
            """)
    List<Object[]> countByBatchAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("batchId") Long batchId, @Param("allBatches") boolean allBatches,
                                         @Param("batchIds") Collection<Long> batchIds);

    /** [studentId, fullName, admissionNumber, photoUrl, currentBatchId, currentBatchName, status, count]. */
    @Query("""
            select s.id, s.fullName, s.admissionNumber, s.photoUrl, sb.id, sb.name, a.status, count(a)
            from Attendance a join a.student s left join s.batch sb
            where a.attendanceDate between :from and :to
              and (:batchId is null or a.batch.id = :batchId)
              and (:allBatches = true or a.batch.id in :batchIds)
            group by s.id, s.fullName, s.admissionNumber, s.photoUrl, sb.id, sb.name, a.status
            """)
    List<Object[]> countByStudentAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                           @Param("batchId") Long batchId, @Param("allBatches") boolean allBatches,
                                           @Param("batchIds") Collection<Long> batchIds);

    /** [studentId, status, absenceReason] of every mark on one day in these batches. */
    @Query("""
            select a.student.id, a.status, a.absenceReason from Attendance a
            where a.attendanceDate = :date and a.batch.id in :batchIds
            """)
    List<Object[]> findDayMarks(@Param("date") LocalDate date, @Param("batchIds") Collection<Long> batchIds);

    /** [status, absenceReason, count] of the absent and excused marks of a period in these batches. */
    @Query("""
            select a.status, a.absenceReason, count(a) from Attendance a
            where a.attendanceDate between :from and :to and a.batch.id in :batchIds
              and a.status in (com.coyotai.education.attendance.AttendanceStatus.ABSENT, com.coyotai.education.attendance.AttendanceStatus.EXCUSED)
            group by a.status, a.absenceReason
            """)
    List<Object[]> countAwayByReason(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                     @Param("batchIds") Collection<Long> batchIds);

    /** Absent marks of a period in these batches that have a reason (for demo data). */
    @Query("""
            select count(a) from Attendance a
            where a.attendanceDate between :from and :to and a.batch.id in :batchIds
              and a.status = com.coyotai.education.attendance.AttendanceStatus.ABSENT and a.absenceReason is not null
            """)
    long countAbsentWithReason(@Param("from") LocalDate from, @Param("to") LocalDate to,
                               @Param("batchIds") Collection<Long> batchIds);

    /** Whole-day marks of a period in these batches (for demo data). */
    @Query("""
            select a from Attendance a join fetch a.student join fetch a.batch
            where a.attendanceDate between :from and :to and a.sessionKey = 0 and a.batch.id in :batchIds
            """)
    List<Attendance> findWholeDayMarks(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                       @Param("batchIds") Collection<Long> batchIds);

    /** [studentId, date] of every student with a mark on a day of the period (demo data fills the gaps). */
    @Query("select distinct a.student.id, a.attendanceDate from Attendance a where a.attendanceDate between :from and :to")
    List<Object[]> findStudentDays(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every mark of one student in a period, oldest first. */
    @Query("""
            select a from Attendance a join fetch a.student s join fetch a.batch b
            left join fetch a.classSchedule cs left join fetch cs.subject
            where s.id = :studentId and a.attendanceDate between :from and :to
            order by a.attendanceDate, a.sessionKey
            """)
    List<Attendance> findForStudent(@Param("studentId") Long studentId, @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
