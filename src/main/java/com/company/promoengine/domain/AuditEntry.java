package com.company.promoengine.domain;

import java.math.BigDecimal;

public record AuditEntry(
        String ruleId,
        String ruleName,
        RuleType type,
        RuleStatus status,
        BigDecimal discountAmount,
        String reason
) {
}
