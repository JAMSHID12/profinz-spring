package com.coyotai.education.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    List<AcademicYear> findAllByOrderByStartDateDesc();

    Optional<AcademicYear> findFirstByCurrentTrue();

    boolean existsByNameIgnoreCase(String name);

    @Modifying
    @Query("update AcademicYear y set y.current = false where y.id <> :keepId and y.current = true")
    int clearCurrentExcept(@Param("keepId") Long keepId);
}
