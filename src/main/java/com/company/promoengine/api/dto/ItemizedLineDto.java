package com.company.promoengine.api.dto;

import java.math.BigDecimal;

public record ItemizedLineDto(
        String sku,
        String category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal originalExtendedPrice,
        BigDecimal discount,
        BigDecimal finalExtendedPrice
) {
}
