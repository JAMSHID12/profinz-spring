package com.coyotai.education.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {

    @Query("select r from ExamResult r join fetch r.student s left join fetch s.user where r.exam.id = :examId")
    List<ExamResult> findByExam(@Param("examId") Long examId);

    @Query("""
            select r from ExamResult r join fetch r.exam e join fetch e.subject join fetch e.examType
            where r.student.id = :studentId
              and e.status = com.coyotai.education.assessment.PublicationStatus.PUBLISHED
              and e.examDate between :from and :to
            order by e.examDate desc
            """)
    List<ExamResult> findPublishedForStudent(@Param("studentId") Long studentId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Query("select r.exam.id, count(r) from ExamResult r where r.exam.id in :examIds group by r.exam.id")
    List<Object[]> countByExams(@Param("examIds") Collection<Long> examIds);
}
