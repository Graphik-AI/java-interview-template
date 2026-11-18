package com.company.promoengine.engine;

import com.company.promoengine.domain.AuditEntry;
import com.company.promoengine.domain.Cart;
import com.company.promoengine.domain.CartItem;
import com.company.promoengine.domain.EvaluationResult;
import com.company.promoengine.domain.ItemizedLine;
import com.company.promoengine.domain.Money;
import com.company.promoengine.domain.PromotionRule;
import com.company.promoengine.domain.RuleStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Placeholder engine. It prices each line at {@code unitPrice * quantity} and
 * grants no discounts, so the application boots and the no-promotion path is real.
 *
 * <p>TODO: replace this body with the README evaluation algorithm
 * (stable priority order, validity window, exclusive groups, running subtotal,
 * per-line HALF_UP discounts, cart-level allocation, and a 0.00 floor).
 * Every input rule must produce one audit entry. The skip reason below is only
 * a stub and is not part of the contract.
 */
@Component
public class DefaultPromotionEngine implements PromotionEngine {

    static final String NOT_IMPLEMENTED = "Skipped: promotion engine not implemented";

    @Override
    public EvaluationResult evaluate(Cart cart, List<PromotionRule> promotions) {
        Objects.requireNonNull(cart, "cart");
        Objects.requireNonNull(promotions, "promotions");

        var lines = cart.items().stream().map(DefaultPromotionEngine::unchangedLine).toList();
        var original = lines.stream()
                .map(ItemizedLine::originalExtendedPrice)
                .reduce(Money.ZERO, BigDecimal::add);

        var audit = promotions.stream()
                .map(rule -> new AuditEntry(
                        rule.id(),
                        rule.name(),
                        rule.type(),
                        RuleStatus.SKIPPED,
                        Money.ZERO,
                        NOT_IMPLEMENTED))
                .toList();

        return new EvaluationResult(
                cart.cartId(),
                cart.currency(),
                original,
                Money.ZERO,
                original,
                lines,
                audit);
    }

    private static ItemizedLine unchangedLine(CartItem item) {
        var extended = Money.extended(item.unitPrice(), item.quantity());
        return new ItemizedLine(
                item.sku(),
                item.category(),
                item.quantity(),
                Money.of(item.unitPrice()),
                extended,
                Money.ZERO,
                extended);
    }
}
