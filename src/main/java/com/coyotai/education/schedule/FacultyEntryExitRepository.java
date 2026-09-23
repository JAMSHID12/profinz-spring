package com.coyotai.education.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FacultyEntryExitRepository extends JpaRepository<FacultyEntryExit, Long> {

    boolean existsByFacultyIdAndEntryDateAndSessionLabelIgnoreCase(Long facultyId, LocalDate entryDate, String sessionLabel);

    @Query("""
            select e from FacultyEntryExit e join fetch e.faculty f
            where e.entryDate between :from and :to
              and (:facultyId is null or f.id = :facultyId)
            order by e.entryDate desc, e.entryTime desc
            """)
    List<FacultyEntryExit> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("facultyId") Long facultyId);
}
