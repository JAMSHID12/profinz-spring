package com.coyotai.education.discipline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DisciplineRecordRepository extends JpaRepository<DisciplineRecord, Long> {

    @Query("""
            select d from DisciplineRecord d
            join fetch d.student s join fetch d.disciplineType left join fetch d.batch b
            where (:studentId is null or s.id = :studentId)
              and (:batchId is null or b.id = :batchId)
              and d.incidentDate between :from and :to
              and (:allBatches = true or s.batch.id in :batchIds)
            order by d.incidentDate desc, d.id desc
            """)
    List<DisciplineRecord> search(@Param("studentId") Long studentId, @Param("batchId") Long batchId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("allBatches") boolean allBatches,
                                  @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select d from DisciplineRecord d join fetch d.disciplineType
            where d.student.id = :studentId and d.incidentDate between :from and :to
            order by d.incidentDate desc
            """)
    List<DisciplineRecord> findForStudent(@Param("studentId") Long studentId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** [studentId, type code, incident date, status] of a period's records, for students now in these batches. */
    @Query("""
            select d.student.id, t.code, d.incidentDate, d.status from DisciplineRecord d join d.disciplineType t
            where d.incidentDate between :from and :to and d.student.batch.id in :batchIds
            """)
    List<Object[]> findForDashboard(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                    @Param("batchIds") Collection<Long> batchIds);

    /** Records created from observations on these attendance marks. */
    @Query("select d from DisciplineRecord d join fetch d.disciplineType where d.attendance.id in :attendanceIds")
    List<DisciplineRecord> findByAttendanceIds(@Param("attendanceIds") Collection<Long> attendanceIds);
}
