package com.company.promoengine.api.dto;

import com.company.promoengine.domain.RuleStatus;
import com.company.promoengine.domain.RuleType;
import java.math.BigDecimal;

public record AuditEntryDto(
        String ruleId,
        String ruleName,
        RuleType type,
        RuleStatus status,
        BigDecimal discountAmount,
        String reason
) {
}
