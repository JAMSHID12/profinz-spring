package com.coyotai.education.assessment;

import com.coyotai.education.common.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Marks rules shared by tests and exams: 0 <= marks <= maximum, and absentees have no marks. */
public final class MarksValidator {

    private MarksValidator() {
    }

    /** Returns the marks to store (null for an absent student), or throws when invalid. */
    public static BigDecimal validate(BigDecimal marks, boolean absent, BigDecimal maxMarks, String studentName) {
        if (absent) {
            return null;
        }
        if (marks == null) {
            throw new BusinessRuleException("Enter marks for " + studentName + " or mark them absent");
        }
        if (marks.signum() < 0) {
            throw new BusinessRuleException("Marks for " + studentName + " cannot be negative");
        }
        if (marks.compareTo(maxMarks) > 0) {
            throw new BusinessRuleException("Marks for " + studentName + " (" + marks.toPlainString()
                    + ") exceed the maximum of " + maxMarks.toPlainString());
        }
        return marks.setScale(2, RoundingMode.HALF_UP);
    }

    /** Percentage with one decimal, or null when absent / no marks. */
    public static Double percentage(BigDecimal marks, BigDecimal maxMarks) {
        if (marks == null || maxMarks == null || maxMarks.signum() == 0) {
            return null;
        }
        return marks.multiply(BigDecimal.valueOf(100))
                .divide(maxMarks, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
