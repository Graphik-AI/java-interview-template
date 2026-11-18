package com.company.promoengine.domain;

import java.time.Instant;
import java.util.List;

public record Cart(
        String cartId,
        String currency,
        Instant evaluationTimestamp,
        UserTier userTier,
        List<CartItem> items,
        String couponCode,
        String paymentMethod
) {
    public Cart {
        items = List.copyOf(items);
    }
}
