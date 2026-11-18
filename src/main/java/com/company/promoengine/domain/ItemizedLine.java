package com.company.promoengine.domain;

import java.math.BigDecimal;

/**
 * One cart line after evaluation. {@code unitPrice} is the input price.
 * {@code discount} is the total taken off this line by every applied rule.
 */
public record ItemizedLine(
        String sku,
        String category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal originalExtendedPrice,
        BigDecimal discount,
        BigDecimal finalExtendedPrice
) {
}
