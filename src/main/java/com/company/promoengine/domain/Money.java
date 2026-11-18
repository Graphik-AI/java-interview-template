package com.company.promoengine.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Scale and rounding for every monetary amount on the public contract.
 * Percentages are not money and must not pass through {@link #of(BigDecimal)}.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal extended(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING);
    }

    /**
     * Audit-reason form: exactly two fraction digits, no exponent ({@code 2880.00}).
     */
    public static String format(BigDecimal amount) {
        return of(amount).toPlainString();
    }
}
