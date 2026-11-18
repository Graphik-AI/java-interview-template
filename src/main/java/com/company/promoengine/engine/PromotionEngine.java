package com.company.promoengine.engine;

import com.company.promoengine.domain.Cart;
import com.company.promoengine.domain.EvaluationResult;
import com.company.promoengine.domain.PromotionRule;
import java.util.List;

/**
 * Stateless pricing of one cart against the candidate rules supplied with it.
 * Implementations must not persist carts or remember earlier calls.
 */
public interface PromotionEngine {

    /**
     * @param promotions every rule the caller asked to consider, already normalized
     * @return totals, per-line prices, and one audit entry per input rule
     */
    EvaluationResult evaluate(Cart cart, List<PromotionRule> promotions);
}
