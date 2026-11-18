package com.company.promoengine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * Benefit parameters. Which fields apply depends on {@code type}.
 * A combination the engine cannot apply is skipped, not rejected.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscountConfigDto(
        BigDecimal percentage,
        BigDecimal amount,
        Integer buyQuantity,
        Integer freeQuantity,
        BigDecimal maxDiscountAmount,
        BigDecimal bundlePrice,
        List<@Valid SpendTierDto> tiers
) {
}
