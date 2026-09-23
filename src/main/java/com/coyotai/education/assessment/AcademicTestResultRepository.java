package com.coyotai.education.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AcademicTestResultRepository extends JpaRepository<AcademicTestResult, Long> {

    @Query("select r from AcademicTestResult r join fetch r.student where r.test.id = :testId")
    List<AcademicTestResult> findByTest(@Param("testId") Long testId);

    /** Published results of one student - what the student portal and performance use. */
    @Query("""
            select r from AcademicTestResult r join fetch r.test t join fetch t.subject
            where r.student.id = :studentId
              and t.status = com.coyotai.education.assessment.PublicationStatus.PUBLISHED
              and (:type is null or t.testType = :type)
              and t.testDate between :from and :to
            order by t.testDate desc
            """)
    List<AcademicTestResult> findPublishedForStudent(@Param("studentId") Long studentId,
                                                     @Param("type") AcademicTest.Type type,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    @Query("select r.test.id, count(r) from AcademicTestResult r where r.test.id in :testIds group by r.test.id")
    List<Object[]> countByTests(@Param("testIds") Collection<Long> testIds);
}
