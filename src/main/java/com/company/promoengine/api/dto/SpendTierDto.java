package com.company.promoengine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpendTierDto(
        @NotNull @DecimalMin(value = "0.00") BigDecimal minSubtotal,
        BigDecimal percentage,
        BigDecimal amount
) {
}
