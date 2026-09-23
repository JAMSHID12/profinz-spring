package com.coyotai.education.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassRegisterEntryRepository extends JpaRepository<ClassRegisterEntry, Long> {

    Optional<ClassRegisterEntry> findByClassScheduleId(Long scheduleId);

    @Query("""
            select e from ClassRegisterEntry e
            join fetch e.classSchedule s join fetch s.batch b join fetch s.subject join fetch s.course
            left join fetch s.faculty left join fetch e.faculty f
            where s.scheduleDate between :from and :to
              and (:facultyId is null or f.id = :facultyId)
              and (:allBatches = true or b.id in :batchIds)
            order by s.scheduleDate desc, s.startTime desc
            """)
    List<ClassRegisterEntry> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                    @Param("facultyId") Long facultyId,
                                    @Param("allBatches") boolean allBatches,
                                    @Param("batchIds") Collection<Long> batchIds);
}
