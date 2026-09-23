package com.coyotai.education.fee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = """
            select p from Payment p join fetch p.student s join fetch p.installment i join fetch i.studentFee
            where (:studentId is null or s.id = :studentId)
              and p.paymentDate between :from and :to
            order by p.paymentDate desc, p.id desc
            """,
            countQuery = """
            select count(p) from Payment p
            where (:studentId is null or p.student.id = :studentId) and p.paymentDate between :from and :to
            """)
    Page<Payment> search(@Param("studentId") Long studentId, @Param("from") LocalDate from,
                         @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select p from Payment p join fetch p.installment i join fetch i.studentFee
            where p.student.id = :studentId order by p.paymentDate desc, p.id desc
            """)
    List<Payment> findForStudent(@Param("studentId") Long studentId);

    @Query("""
            select p from Payment p
            join fetch p.student s join fetch p.installment i join fetch i.studentFee f
            where p.id = :id
            """)
    Optional<Payment> findDetail(@Param("id") Long id);

    /** Highest receipt number with the given prefix; the sequence continues from it. */
    @Query("select max(p.receiptNumber) from Payment p where p.receiptNumber like concat(:prefix, '%')")
    Optional<String> findMaxReceiptNumber(@Param("prefix") String prefix);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.paymentDate between :from and :to")
    BigDecimal sumCollectedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
