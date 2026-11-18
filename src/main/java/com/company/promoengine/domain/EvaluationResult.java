package com.company.promoengine.domain;

import java.math.BigDecimal;
import java.util.List;

public record EvaluationResult(
        String cartId,
        String currency,
        BigDecimal originalSubtotal,
        BigDecimal totalDiscount,
        BigDecimal finalTotal,
        List<ItemizedLine> itemizedBreakdown,
        List<AuditEntry> auditTrail
) {
    public EvaluationResult {
        itemizedBreakdown = List.copyOf(itemizedBreakdown);
        auditTrail = List.copyOf(auditTrail);
    }
}
