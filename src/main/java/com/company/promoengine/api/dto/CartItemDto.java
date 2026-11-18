package com.company.promoengine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CartItemDto(
        @NotBlank String sku,
        @NotBlank String category,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
        @Min(1) int quantity
) {
}
