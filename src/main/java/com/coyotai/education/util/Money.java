package com.coyotai.education.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Currency amounts are always 2-decimal, non-negative BigDecimals. */
public final class Money {

    public static final BigDecimal ZERO = scale(BigDecimal.ZERO);

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Subtraction that never produces a negative balance. */
    public static BigDecimal subtractFloorZero(BigDecimal minuend, BigDecimal subtrahend) {
        BigDecimal result = scale(minuend).subtract(scale(subtrahend));
        return result.signum() < 0 ? ZERO : scale(result);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return scale(scale(a).add(scale(b)));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
