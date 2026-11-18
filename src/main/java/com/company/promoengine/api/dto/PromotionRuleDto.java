package com.company.promoengine.api.dto;

import com.company.promoengine.domain.RuleType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionRuleDto(
        @NotBlank String id,
        @NotBlank String name,
        @NotNull RuleType type,
        @Min(1) int priority,
        String exclusiveGroup,
        Boolean stackableWithItemPromos,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        @NotNull @Valid RuleConditionsDto conditions,
        @NotNull @Valid DiscountConfigDto discountConfig
) {
}
