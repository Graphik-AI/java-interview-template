package com.company.promoengine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleConditionsDto(
        String category,
        List<@NotNull String> skus,
        String couponCode,
        String paymentMethod,
        List<@Valid BundleComponentDto> components
) {
}
