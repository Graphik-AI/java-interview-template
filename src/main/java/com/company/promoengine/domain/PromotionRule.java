package com.company.promoengine.domain;

import java.time.Instant;

/**
 * {@code stackableWithItemPromos} is read by cart-level rules.
 * Null and {@code true} both mean the rule stacks. {@code false} limits the
 * cart base to lines that no item-level rule has reduced.
 */
public record PromotionRule(
        String id,
        String name,
        RuleType type,
        int priority,
        String exclusiveGroup,
        Boolean stackableWithItemPromos,
        Instant validFrom,
        Instant validTo,
        RuleConditions conditions,
        DiscountConfig discountConfig
) {
}
