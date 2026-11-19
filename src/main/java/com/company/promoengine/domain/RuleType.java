package com.company.promoengine.domain;

/**
 * Rule types accepted by {@code POST /api/v1/cart/evaluate}.
 * Required fields, ordering, and effects are specified in the README.
 */
public enum RuleType {
    /** Percentage off the current price of non-bundled units in one category. */
    PERCENTAGE_OFF_CATEGORY,

    /** Across eligible SKUs, the cheapest units are free. */
    BUY_X_GET_CHEAPEST_FREE,

    /** A fixed price for each complete set of component SKUs. */
    BUNDLE_FIXED_PRICE,

    /** One spend tier, chosen from the running cart base. */
    TIERED_SPEND,

    /** Percentage off the running cart base. */
    CART_PERCENTAGE_OFF,

    /** Cart discount that requires a coupon code and, when set, a payment method. */
    COUPON_PAYMENT_OFF
}
