package com.company.promoengine.engine;

import com.company.promoengine.domain.AuditEntry;
import com.company.promoengine.domain.Cart;
import com.company.promoengine.domain.EvaluationResult;
import com.company.promoengine.domain.ItemizedLine;
import com.company.promoengine.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The running cart during a single evaluation: the input {@link Cart} plus one
 * {@link LineState} per cart line, in original request order.
 *
 * <p>Every total is derived from the lines' current unit prices, never tracked
 * separately, so the accounting invariants hold by construction. This class does not
 * order rules, run gates, or write audit entries.
 */
final class CartState {

    private final Cart cart;
    private final List<LineState> lines;

    CartState(Cart cart) {
        this.cart = Objects.requireNonNull(cart, "cart");
        this.lines = cart.items().stream().map(LineState::new).toList();
    }

    Cart cart() {
        return cart;
    }

    /** All lines in request order (unmodifiable list; index = request position). */
    List<LineState> lines() {
        return lines;
    }

    LineState line(int index) {
        return lines.get(index);
    }

    BigDecimal originalSubtotal() {
        return sum(lines, LineState::originalTotal);
    }

    BigDecimal currentSubtotal() {
        return sum(lines, LineState::currentTotal);
    }

    BigDecimal totalDiscount() {
        return originalSubtotal().subtract(currentSubtotal());
    }

    /** One {@link ItemizedLine} per line, in request order. */
    List<ItemizedLine> itemizedLines() {
        return lines.stream().map(LineState::toItemizedLine).toList();
    }

    /**
     * Builds the final result. Totals are summed from the itemized lines themselves, so
     * {@code originalSubtotal = sum(originalExtendedPrice)},
     * {@code finalTotal = sum(finalExtendedPrice)}, and
     * {@code totalDiscount = originalSubtotal - finalTotal = sum(line.discount)}.
     * The audit trail is passed through unchanged.
     */
    EvaluationResult toResult(List<AuditEntry> auditTrail) {
        Objects.requireNonNull(auditTrail, "auditTrail");
        List<ItemizedLine> items = itemizedLines();
        BigDecimal original = sum(items, ItemizedLine::originalExtendedPrice);
        BigDecimal finalTotal = sum(items, ItemizedLine::finalExtendedPrice);
        return new EvaluationResult(
                cart.cartId(),
                cart.currency(),
                original,
                original.subtract(finalTotal),
                finalTotal,
                items,
                auditTrail);
    }

    private static <T> BigDecimal sum(List<T> values, Function<T, BigDecimal> amount) {
        return values.stream().map(amount).reduce(Money.ZERO, BigDecimal::add);
    }
}
