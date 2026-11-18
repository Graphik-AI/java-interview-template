package com.company.promoengine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateCartRequest(
        @NotNull @Valid CartDto cart,
        @NotNull List<@NotNull @Valid PromotionRuleDto> promotions
) {
}
