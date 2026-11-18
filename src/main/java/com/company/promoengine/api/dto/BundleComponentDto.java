package com.company.promoengine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BundleComponentDto(
        @NotBlank String sku,
        @Min(1) int quantity
) {
}
