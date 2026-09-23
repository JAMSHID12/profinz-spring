package com.coyotai.education.fee;

import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure fee arithmetic, kept free of persistence so it can be unit tested directly. */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    /**
     * Recomputes pending amount and status. Pending never goes negative; a fully paid
     * installment is PAID even after its due date; WAIVED stays WAIVED.
     */
    public static void recalculate(FeeInstallment installment, LocalDate today) {
        BigDecimal amount = Money.scale(installment.getAmount());
        BigDecimal paid = Money.scale(installment.getPaidAmount());
        installment.setAmount(amount);
        installment.setPaidAmount(paid);
        if (installment.getStatus() == InstallmentStatus.WAIVED) {
            installment.setPendingAmount(Money.ZERO);
            return;
        }
        BigDecimal pending = Money.subtractFloorZero(amount, paid);
        installment.setPendingAmount(pending);
        if (pending.signum() == 0) {
            installment.setStatus(InstallmentStatus.PAID);
        } else if (installment.getDueDate() != null && installment.getDueDate().isBefore(today)) {
            installment.setStatus(InstallmentStatus.OVERDUE);
        } else if (Money.isPositive(paid)) {
            installment.setStatus(InstallmentStatus.PARTIAL);
        } else {
            installment.setStatus(InstallmentStatus.PENDING);
        }
    }

    /**
     * Applies a new net amount (after a discount change) to a plan's installments.
     *
     * <p>Paid and waived installments never change, and no installment drops below what has
     * already been paid on it. The balance still to be paid is divided equally over the open
     * installments (pending, part paid or overdue). Returns the open installments that end up
     * with nothing to pay and nothing paid, so the caller can remove them.
     *
     * @throws BusinessRuleException when the payments already made exceed the new net amount,
     *                               or every installment is settled and the amount would change
     */
    public static List<FeeInstallment> redistribute(List<FeeInstallment> installments, BigDecimal newNet,
                                                    LocalDate today) {
        List<FeeInstallment> open = installments.stream()
                .filter(FeeCalculator::isOpen)
                .sorted(Comparator.comparingInt(FeeInstallment::getInstallmentNo))
                .toList();
        BigDecimal minimum = minimumNet(installments);
        BigDecimal stillToPay = Money.scale(newNet).subtract(minimum);

        if (stillToPay.signum() < 0) {
            throw new BusinessRuleException("Payments already made on this plan come to more than the new amount; "
                    + "the fee after discount cannot be lower than " + minimum.toPlainString());
        }
        if (open.isEmpty()) {
            if (stillToPay.signum() != 0) {
                throw new BusinessRuleException("Every installment is already paid or waived, so the discount "
                        + "can no longer be changed");
            }
            return List.of();
        }

        List<BigDecimal> shares = split(stillToPay, open.size());
        List<FeeInstallment> empty = new ArrayList<>();
        for (int i = 0; i < open.size(); i++) {
            FeeInstallment installment = open.get(i);
            installment.setAmount(Money.add(installment.getPaidAmount(), shares.get(i)));
            recalculate(installment, today);
            if (installment.getAmount().signum() == 0) {
                empty.add(installment);
            }
        }
        return empty;
    }

    /**
     * The lowest net amount a plan can be reduced to: everything already settled (paid or
     * waived installments) plus what has been paid on the open ones.
     */
    public static BigDecimal minimumNet(List<FeeInstallment> installments) {
        BigDecimal minimum = Money.ZERO;
        for (FeeInstallment installment : installments) {
            minimum = Money.add(minimum, isOpen(installment) ? installment.getPaidAmount() : installment.getAmount());
        }
        return minimum;
    }

    /** Pending, part paid or overdue: installments that can still take payments. */
    public static boolean isOpen(FeeInstallment installment) {
        return installment.getStatus() == InstallmentStatus.PENDING
                || installment.getStatus() == InstallmentStatus.PARTIAL
                || installment.getStatus() == InstallmentStatus.OVERDUE;
    }

    /** Splits an amount into equal installments; the rounding remainder goes to the last one. */
    public static List<BigDecimal> split(BigDecimal total, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("At least one installment is required");
        }
        BigDecimal scaled = Money.scale(total);
        BigDecimal each = scaled.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        List<BigDecimal> parts = new ArrayList<>();
        for (int i = 0; i < count - 1; i++) {
            parts.add(each);
        }
        parts.add(scaled.subtract(each.multiply(BigDecimal.valueOf(count - 1))));
        return parts;
    }

    /** Next receipt number in the series PREFIX-RCPT-YEAR-000001. */
    public static String nextReceiptNumber(String prefix, int year, String currentMax) {
        String series = prefix + "-RCPT-" + year + "-";
        long next = 1;
        if (currentMax != null && currentMax.startsWith(series)) {
            try {
                next = Long.parseLong(currentMax.substring(series.length())) + 1;
            } catch (NumberFormatException ignored) {
                // A hand-made number in the series; start after the numeric receipts.
            }
        }
        return series + String.format("%06d", next);
    }
}
