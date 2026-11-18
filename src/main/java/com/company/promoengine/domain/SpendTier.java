package com.company.promoengine.domain;

import java.math.BigDecimal;

/**
 * A spend threshold and the single benefit granted when that tier is selected.
 * Exactly one of {@code percentage} or {@code amount} is set.
 */
public record SpendTier(BigDecimal minSubtotal, BigDecimal percentage, BigDecimal amount) {
}
