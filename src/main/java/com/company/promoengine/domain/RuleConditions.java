package com.company.promoengine.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-specific predicates. Unused fields are null.
 * Money fields are scale 2 when present. Coupon and payment method comparisons
 * are case-insensitive; category and SKU comparisons are case-sensitive.
 */
public record RuleConditions(
        String category,
        String sku,
        BigDecimal minSubtotal,
        List<String> skus,
        String couponCode,
        String paymentMethod,
        List<BundleComponent> components
) {
    public RuleConditions {
        skus = skus == null ? null : List.copyOf(skus);
        components = components == null ? null : List.copyOf(components);
    }
}
