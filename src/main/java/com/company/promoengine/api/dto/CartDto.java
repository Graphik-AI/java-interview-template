package com.company.promoengine.api.dto;

import com.company.promoengine.domain.UserTier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CartDto(
        @NotBlank String cartId,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Instant evaluationTimestamp,
        @NotNull UserTier userTier,
        @NotNull List<@NotNull @Valid CartItemDto> items,
        String couponCode,
        String paymentMethod
) {
}
