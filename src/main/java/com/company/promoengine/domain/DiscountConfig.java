package com.company.promoengine.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-specific benefit. Unused fields are null.
 * {@code percentage} is a percent ({@code 10} means 10%), not a fraction.
 * {@code amount}, {@code maxDiscountAmount}, and {@code bundlePrice} are money when present.
 */
public record DiscountConfig(
        BigDecimal percentage,
        BigDecimal amount,
        Integer buyQuantity,
        Integer freeQuantity,
        BigDecimal maxDiscountAmount,
        BigDecimal bundlePrice,
        List<SpendTier> tiers
) {
    public DiscountConfig {
        tiers = tiers == null ? null : List.copyOf(tiers);
    }
}
