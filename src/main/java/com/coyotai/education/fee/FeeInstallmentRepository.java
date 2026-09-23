package com.coyotai.education.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, Long> {

    @Query("""
            select i from FeeInstallment i
            join fetch i.studentFee f join fetch i.student s left join fetch s.batch b where (:studentId is null or s.id = :studentId)
              and (:batchId is null or b.id = :batchId)
              and (:status is null or i.status = :status)
              and i.dueDate between :from and :to
            order by i.dueDate, s.fullName, i.installmentNo
            """)
    List<FeeInstallment> search(@Param("studentId") Long studentId, @Param("batchId") Long batchId,
                                @Param("status") InstallmentStatus status,
                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select i from FeeInstallment i
            join fetch i.studentFee f join fetch i.student s left join fetch s.user
            where i.id = :id
            """)
    Optional<FeeInstallment> findDetail(@Param("id") Long id);

    @Query("""
            select i from FeeInstallment i join fetch i.studentFee f
            where i.student.id = :studentId
            order by i.dueDate, i.installmentNo
            """)
    List<FeeInstallment> findForStudent(@Param("studentId") Long studentId);

    /** Unpaid installments of active students that are due - input to the daily reminder job. */
    @Query("""
            select i from FeeInstallment i
            join fetch i.studentFee f join fetch i.student s left join fetch s.user
            where i.pendingAmount > 0
              and i.status in (com.coyotai.education.fee.InstallmentStatus.PENDING, com.coyotai.education.fee.InstallmentStatus.PARTIAL,
                               com.coyotai.education.fee.InstallmentStatus.OVERDUE)
              and f.status = com.coyotai.education.fee.StudentFee.Status.ACTIVE
              and s.status = com.coyotai.education.student.Student.Status.ACTIVE
              and i.dueDate <= :onOrBefore
            order by i.dueDate
            """)
    List<FeeInstallment> findReminderCandidates(@Param("onOrBefore") LocalDate onOrBefore);

    @Query("""
            select i from FeeInstallment i
            where i.pendingAmount > 0 and i.dueDate < :today
              and i.status in (com.coyotai.education.fee.InstallmentStatus.PENDING, com.coyotai.education.fee.InstallmentStatus.PARTIAL)
            """)
    List<FeeInstallment> findNewlyOverdue(@Param("today") LocalDate today);

    @Query("""
            select coalesce(sum(i.pendingAmount), 0) from FeeInstallment i
            where i.status in (com.coyotai.education.fee.InstallmentStatus.PENDING, com.coyotai.education.fee.InstallmentStatus.PARTIAL,
                               com.coyotai.education.fee.InstallmentStatus.OVERDUE)
            """)
    BigDecimal sumOutstanding();

    @Query("select coalesce(sum(i.pendingAmount), 0) from FeeInstallment i where i.status = com.coyotai.education.fee.InstallmentStatus.OVERDUE")
    BigDecimal sumOverdue();

    @Query("""
            select i.status, count(i), coalesce(sum(i.amount), 0), coalesce(sum(i.paidAmount), 0), coalesce(sum(i.pendingAmount), 0)
            from FeeInstallment i where i.dueDate between :from and :to group by i.status
            """)
    List<Object[]> summaryByStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
