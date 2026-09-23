package com.coyotai.education.fee;

import com.coyotai.education.common.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    private FeeInstallment installment(String amount, String paid, LocalDate due) {
        FeeInstallment installment = new FeeInstallment();
        installment.setAmount(new BigDecimal(amount));
        installment.setPaidAmount(new BigDecimal(paid));
        installment.setDueDate(due);
        return installment;
    }

    @Test
    @DisplayName("Unpaid and not yet due is PENDING")
    void pending() {
        FeeInstallment i = installment("20000", "0", TODAY.plusDays(5));
        FeeCalculator.recalculate(i, TODAY);
        assertThat(i.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(i.getPendingAmount()).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("Part paid and not yet due is PARTIAL")
    void partial() {
        FeeInstallment i = installment("20000", "5000", TODAY.plusDays(5));
        FeeCalculator.recalculate(i, TODAY);
        assertThat(i.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
        assertThat(i.getPendingAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Anything outstanding after the due date is OVERDUE")
    void overdue() {
        FeeInstallment i = installment("20000", "5000", TODAY.minusDays(1));
        FeeCalculator.recalculate(i, TODAY);
        assertThat(i.getStatus()).isEqualTo(InstallmentStatus.OVERDUE);
    }

    @Test
    @DisplayName("Fully paid is PAID even after the due date, and pending never goes negative")
    void paidWinsAndNeverNegative() {
        FeeInstallment i = installment("20000", "25000", TODAY.minusDays(30));
        FeeCalculator.recalculate(i, TODAY);
        assertThat(i.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(i.getPendingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A waived installment stays waived with nothing outstanding")
    void waivedStaysWaived() {
        FeeInstallment i = installment("20000", "0", TODAY.minusDays(30));
        i.setStatus(InstallmentStatus.WAIVED);
        FeeCalculator.recalculate(i, TODAY);
        assertThat(i.getStatus()).isEqualTo(InstallmentStatus.WAIVED);
        assertThat(i.getPendingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Splitting puts the rounding remainder on the last installment")
    void splitKeepsTheTotal() {
        List<BigDecimal> parts = FeeCalculator.split(new BigDecimal("10000"), 3);
        assertThat(parts).containsExactly(new BigDecimal("3333.33"), new BigDecimal("3333.33"), new BigDecimal("3333.34"));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10000");
    }

    private FeeInstallment numbered(int no, String amount, String paid) {
        FeeInstallment i = installment(amount, paid, TODAY.plusMonths(no));
        i.setInstallmentNo(no);
        FeeCalculator.recalculate(i, TODAY);
        return i;
    }

    @Test
    @DisplayName("A new discount is spread equally over the installments not yet paid")
    void discountSpreadOverOpenInstallments() {
        // CMA USA fee 20,000 in two installments; nothing paid yet; the student gets 2,000 off.
        List<FeeInstallment> plan = List.of(numbered(1, "10000", "0"), numbered(2, "10000", "0"));
        List<FeeInstallment> empty = FeeCalculator.redistribute(plan, new BigDecimal("18000"), TODAY);
        assertThat(empty).isEmpty();
        assertThat(plan).extracting(FeeInstallment::getAmount)
                .containsExactly(new BigDecimal("9000.00"), new BigDecimal("9000.00"));
    }

    @Test
    @DisplayName("Paid installments and money already paid are never touched")
    void paidPartsAreKept() {
        // First installment paid in full, 4,000 paid on the second; discount raised to 3,000.
        FeeInstallment first = numbered(1, "10000", "10000");
        FeeInstallment second = numbered(2, "10000", "4000");
        List<FeeInstallment> plan = List.of(first, second);
        FeeCalculator.redistribute(plan, new BigDecimal("17000"), TODAY);
        assertThat(first.getAmount()).isEqualByComparingTo("10000.00");
        assertThat(first.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(second.getAmount()).isEqualByComparingTo("7000.00");
        assertThat(second.getPendingAmount()).isEqualByComparingTo("3000.00");
        assertThat(second.getStatus()).isEqualTo(InstallmentStatus.PARTIAL);
    }

    @Test
    @DisplayName("Removing a discount raises what is still due")
    void smallerDiscountRaisesBalance() {
        List<FeeInstallment> plan = List.of(numbered(1, "9000", "9000"), numbered(2, "9000", "0"));
        FeeCalculator.redistribute(plan, new BigDecimal("20000"), TODAY);
        assertThat(plan.get(1).getAmount()).isEqualByComparingTo("11000.00");
    }

    @Test
    @DisplayName("A discount cannot push the fee below what has already been paid")
    void discountCannotGoBelowPaid() {
        List<FeeInstallment> plan = List.of(numbered(1, "10000", "10000"), numbered(2, "10000", "2000"));
        assertThat(FeeCalculator.minimumNet(plan)).isEqualByComparingTo("12000.00");
        assertThatThrownBy(() -> FeeCalculator.redistribute(plan, new BigDecimal("11000"), TODAY))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Installments the discount covers completely are returned for removal")
    void fullyCoveredInstallmentsAreReturned() {
        List<FeeInstallment> plan = List.of(numbered(1, "10000", "10000"), numbered(2, "10000", "0"));
        List<FeeInstallment> empty = FeeCalculator.redistribute(plan, new BigDecimal("10000"), TODAY);
        assertThat(empty).containsExactly(plan.get(1));
    }

    @Test
    @DisplayName("Once everything is settled the amount can no longer change")
    void settledPlanIsFrozen() {
        List<FeeInstallment> plan = List.of(numbered(1, "10000", "10000"), numbered(2, "10000", "10000"));
        assertThatThrownBy(() -> FeeCalculator.redistribute(plan, new BigDecimal("21000"), TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already paid or waived");
    }

    @Test
    @DisplayName("Receipt numbers continue the yearly series of the client prefix")
    void receiptNumbers() {
        assertThat(FeeCalculator.nextReceiptNumber("PROFINZ", 2026, null)).isEqualTo("PROFINZ-RCPT-2026-000001");
        assertThat(FeeCalculator.nextReceiptNumber("PROFINZ", 2026, "PROFINZ-RCPT-2026-000041"))
                .isEqualTo("PROFINZ-RCPT-2026-000042");
        // A new year starts a new series.
        assertThat(FeeCalculator.nextReceiptNumber("PROFINZ", 2027, "PROFINZ-RCPT-2026-000041"))
                .isEqualTo("PROFINZ-RCPT-2027-000001");
        // Another client's prefix never collides.
        assertThat(FeeCalculator.nextReceiptNumber("ACME", 2026, null)).isEqualTo("ACME-RCPT-2026-000001");
    }
}
