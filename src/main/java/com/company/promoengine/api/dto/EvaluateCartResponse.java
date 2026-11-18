package com.company.promoengine.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record EvaluateCartResponse(
        String cartId,
        String currency,
        BigDecimal originalSubtotal,
        BigDecimal totalDiscount,
        BigDecimal finalTotal,
        List<ItemizedLineDto> itemizedBreakdown,
        List<AuditEntryDto> auditTrail
) {
}
