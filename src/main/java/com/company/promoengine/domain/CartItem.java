package com.company.promoengine.domain;

import java.math.BigDecimal;

public record CartItem(
        String sku,
        String category,
        BigDecimal unitPrice,
        int quantity
) {
}
