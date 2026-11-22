package com.company.promoengine.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.promoengine.domain.AuditEntry;
import com.company.promoengine.domain.Cart;
import com.company.promoengine.domain.CartItem;
import com.company.promoengine.domain.EvaluationResult;
import com.company.promoengine.domain.ItemizedLine;
import com.company.promoengine.domain.Money;
import com.company.promoengine.domain.RuleStatus;
import com.company.promoengine.domain.RuleType;
import com.company.promoengine.domain.UserTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LineState} and {@link CartState}.
 * Assertions use {@link BigDecimal#equals}, which also checks scale 2.
 */
class LineStateTest {

    @Nested
    class Initialization {

        @Test
        void everyUnitStartsAtTheInputUnitPrice() {
            var item = item("SKU-A", "BOOKS", "33.33", 3);
            var line = new LineState(item);

            assertThat(line.item()).isSameAs(item);
            assertThat(line.quantity()).isEqualTo(3);
            for (int unit = 0; unit < 3; unit++) {
                assertThat(line.currentUnitPrice(unit)).isEqualTo(money("33.33"));
                assertThat(line.isBundled(unit)).isFalse();
            }
            assertThat(line.isItemDiscounted()).isFalse();
            assertThat(line.originalUnitPrice()).isEqualTo(money("33.33"));
            assertThat(line.originalTotal()).isEqualTo(money("99.99"));
            assertThat(line.currentTotal()).isEqualTo(money("99.99"));
            assertThat(line.discount()).isEqualTo(money("0.00"));
        }

        @Test
        void unscaledUnitPriceIsNormalizedToScaleTwo() {
            var line = new LineState(item("SKU-MUG", "KITCHEN", "120.0", 3));

            assertThat(line.originalUnitPrice()).isEqualTo(money("120.00"));
            assertThat(line.currentUnitPrice(0)).isEqualTo(money("120.00"));
            assertThat(line.originalTotal()).isEqualTo(money("360.00"));
        }

        @Test
        void rejectsNegativeUnitPrice() {
            assertThatThrownBy(() -> new LineState(item("SKU-A", "BOOKS", "-0.01", 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class TotalsAndEligibleBase {

        @Test
        void withNothingBundledEveryUnitIsEligible() {
            var line = new LineState(item("SKU-A", "BOOKS", "10.00", 3));

            assertThat(line.allUnits()).containsExactly(0, 1, 2);
            assertThat(line.nonBundledUnits()).containsExactly(0, 1, 2);
            assertThat(line.nonBundledTotal()).isEqualTo(money("30.00"));
            assertThat(line.currentTotalOf(new int[] {0, 2})).isEqualTo(money("20.00"));
        }

        @Test
        void unitIndexesAreBoundsChecked() {
            var line = new LineState(item("SKU-A", "BOOKS", "10.00", 2));

            assertThatThrownBy(() -> line.currentUnitPrice(2)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> line.isBundled(-1)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> line.currentTotalOf(new int[] {0, 5}))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    class Reduce {

        @Test
        void splitsAmountAcrossUnitsWithPennyAllocation() {
            var line = new LineState(item("SKU-A", "BOOKS", "10.00", 3));

            line.reduce(line.allUnits(), money("10.00")); // shares 3.34, 3.33, 3.33

            assertThat(unitPrices(line)).containsExactly(money("6.66"), money("6.67"), money("6.67"));
            assertThat(line.currentTotal()).isEqualTo(money("20.00"));
            assertThat(line.discount()).isEqualTo(money("10.00"));
        }

        @Test
        void touchesOnlyTheSelectedUnits() {
            var line = new LineState(item("SKU-LAPTOP", "LAPTOP", "800.00", 3));

            line.reduce(new int[] {2}, money("80.00"));

            assertThat(unitPrices(line)).containsExactly(money("800.00"), money("800.00"), money("720.00"));
        }

        @Test
        void splitIsWeightedByCurrentUnitPrice() {
            var line = new LineState(item("SKU-A", "BOOKS", "10.00", 3));
            line.reduce(new int[] {0}, money("5.00")); // units: 5.00, 10.00, 10.00

            line.reduce(line.allUnits(), money("5.00")); // weights 5:10:10 -> 1.00, 2.00, 2.00

            assertThat(unitPrices(line)).containsExactly(money("4.00"), money("8.00"), money("8.00"));
        }

        @Test
        void reducingByTheFullPriceLandsExactlyOnZero() {
            var line = new LineState(item("SKU-MUG", "KITCHEN", "120.00", 3));

            line.reduce(new int[] {0}, money("120.00"));

            assertThat(line.currentUnitPrice(0)).isEqualTo(money("0.00"));
            assertThat(line.currentTotal()).isEqualTo(money("240.00"));
        }

        @Test
        void zeroAmountChangesNothing() {
            var line = new LineState(item("SKU-A", "BOOKS", "10.00", 2));

            line.reduce(line.allUnits(), money("0.00"));

            assertThat(unitPrices(line)).containsExactly(money("10.00"), money("10.00"));
        }

        @Test
        void doesNotMarkTheLineItemDiscountedByItself() {
            var line = new LineState(item("SKU-A", "BOOKS", "10.00", 1));

            line.reduce(line.allUnits(), money("1.00"));

            assertThat(line.isItemDiscounted()).isFalse();
            line.markItemDiscounted();
            assertThat(line.isItemDiscounted()).isTrue();
        }

        @Test
        void zeroFloorRejectsAmountAboveSelectedUnitsAndLeavesStateUnchanged() {
            var line = new LineState(item("SKU-A", "BOOKS", "5.00", 2));

            assertThatThrownBy(() -> line.reduce(new int[] {0}, money("5.01")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(unitPrices(line)).containsExactly(money("5.00"), money("5.00"));

            line.reduce(line.allUnits(), money("10.00"));
            assertThat(unitPrices(line)).containsExactly(money("0.00"), money("0.00"));
            assertThat(line.currentTotal()).isEqualTo(money("0.00"));

            assertThatThrownBy(() -> line.reduce(line.allUnits(), money("0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(line.currentTotal()).isEqualTo(money("0.00"));
        }

        @Test
        void rejectsNegativeAmount() {
            var line = new LineState(item("SKU-A", "BOOKS", "5.00", 1));

            assertThatThrownBy(() -> line.reduce(line.allUnits(), money("-1.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBadIndexesWithoutChangingState() {
            var line = new LineState(item("SKU-A", "BOOKS", "1.00", 2));

            assertThatThrownBy(() -> line.reduce(new int[] {2}, money("0.50")))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> line.reduce(new int[] {-1}, money("0.50")))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            // A repeated index would double-count unit 0's price and could push it below zero.
            assertThatThrownBy(() -> line.reduce(new int[] {0, 0}, money("2.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> line.reduce(null, money("0.50")))
                    .isInstanceOf(NullPointerException.class);

            assertThat(unitPrices(line)).containsExactly(money("1.00"), money("1.00"));
        }
    }

    @Nested
    class Bundling {

        @Test
        void bundledUnitsLeaveTheEligibleSetButLeftoversRemain() {
            var line = new LineState(item("SKU-LAPTOP", "LAPTOP", "800.00", 3));

            line.markBundled(new int[] {0, 1});

            assertThat(line.isBundled(0)).isTrue();
            assertThat(line.isBundled(1)).isTrue();
            assertThat(line.isBundled(2)).isFalse();
            assertThat(line.nonBundledUnits()).containsExactly(2);
            assertThat(line.nonBundledTotal()).isEqualTo(money("800.00"));
            // Bundling alone does not change any price.
            assertThat(line.currentTotal()).isEqualTo(money("2400.00"));
            assertThat(line.allUnits()).containsExactly(0, 1, 2);
        }

        @Test
        void bundledUnitsCanStillBeReduced() {
            var line = new LineState(item("SKU-LAPTOP", "LAPTOP", "800.00", 3));
            line.markBundled(new int[] {0, 1});

            line.reduce(new int[] {0, 1}, money("320.00"));

            assertThat(unitPrices(line)).containsExactly(money("640.00"), money("640.00"), money("800.00"));
            assertThat(line.nonBundledTotal()).isEqualTo(money("800.00"));
        }

        @Test
        void fullyBundledLineHasZeroEligibleBase() {
            var line = new LineState(item("SKU-BAG", "ACCESSORIES", "50.00", 2));

            line.markBundled(line.allUnits());

            assertThat(line.nonBundledUnits()).isEmpty();
            assertThat(line.nonBundledTotal()).isEqualTo(money("0.00"));
        }

        @Test
        void aUnitCannotBeBundledTwice() {
            var line = new LineState(item("SKU-BAG", "ACCESSORIES", "50.00", 2));
            line.markBundled(new int[] {0});

            assertThatThrownBy(() -> line.markBundled(new int[] {1, 0}))
                    .isInstanceOf(IllegalArgumentException.class);
            // Validation happens before any change, so unit 1 was not marked.
            assertThat(line.isBundled(1)).isFalse();
            assertThatThrownBy(() -> line.markBundled(new int[] {2}))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    class CartStateBehaviour {

        @Test
        void keepsLinesInRequestOrder() {
            var items = List.of(
                    item("SKU-C", "Z", "1.00", 1),
                    item("SKU-A", "A", "2.00", 1),
                    item("SKU-B", "M", "3.00", 1));
            var state = new CartState(cart(items));

            assertThat(state.lines()).extracting(LineState::item).containsExactlyElementsOf(items);
            assertThat(state.line(1).item()).isSameAs(items.get(1));
            assertThat(state.itemizedLines()).extracting(ItemizedLine::sku)
                    .containsExactly("SKU-C", "SKU-A", "SKU-B");
        }

        @Test
        void subtotalsAndTotalDiscountFollowLineChanges() {
            var state = new CartState(cart(List.of(
                    item("SKU-SHOES", "FOOTWEAR", "2000.00", 1),
                    item("SKU-SOCKS", "ACCESSORIES", "500.00", 1))));

            assertThat(state.originalSubtotal()).isEqualTo(money("2500.00"));
            assertThat(state.currentSubtotal()).isEqualTo(money("2500.00"));
            assertThat(state.totalDiscount()).isEqualTo(money("0.00"));

            state.line(0).reduce(state.line(0).allUnits(), money("200.00"));

            assertThat(state.originalSubtotal()).isEqualTo(money("2500.00"));
            assertThat(state.currentSubtotal()).isEqualTo(money("2300.00"));
            assertThat(state.totalDiscount()).isEqualTo(money("200.00"));
        }

        @Test
        void resultSatisfiesAccountingInvariants() {
            // README example: 3 laptops, 2 mice, 2 bags -> 2 bundles, then 10% off the leftover laptop.
            var state = new CartState(cart(List.of(
                    item("SKU-LAPTOP", "LAPTOP", "800.00", 3),
                    item("SKU-MOUSE", "ACCESSORIES", "150.00", 2),
                    item("SKU-BAG", "ACCESSORIES", "50.00", 2))));
            applyBundle(state.line(0), new int[] {0, 1}, "320.00");
            applyBundle(state.line(1), new int[] {0, 1}, "60.00");
            applyBundle(state.line(2), new int[] {0, 1}, "20.00");
            state.line(0).reduce(state.line(0).nonBundledUnits(), money("80.00"));
            var audit = List.of(
                    audit("PROMO_DESK", RuleType.BUNDLE_FIXED_PRICE, "400.00"),
                    audit("PROMO_LAPTOP", RuleType.PERCENTAGE_OFF_CATEGORY, "80.00"));

            EvaluationResult result = state.toResult(audit);

            assertThat(result.cartId()).isEqualTo("cart-1");
            assertThat(result.currency()).isEqualTo("INR");
            assertThat(result.originalSubtotal()).isEqualTo(money("2800.00"));
            assertThat(result.finalTotal()).isEqualTo(money("2320.00"));
            assertThat(result.totalDiscount()).isEqualTo(money("480.00"));
            assertThat(result.itemizedBreakdown()).containsExactly(
                    new ItemizedLine("SKU-LAPTOP", "LAPTOP", 3, money("800.00"),
                            money("2400.00"), money("400.00"), money("2000.00")),
                    new ItemizedLine("SKU-MOUSE", "ACCESSORIES", 2, money("150.00"),
                            money("300.00"), money("60.00"), money("240.00")),
                    new ItemizedLine("SKU-BAG", "ACCESSORIES", 2, money("50.00"),
                            money("100.00"), money("20.00"), money("80.00")));
            assertThat(result.auditTrail()).containsExactlyElementsOf(audit);
            assertInvariants(result);
        }

        @Test
        void lineCanBeReducedToZeroButNotBelow() {
            var state = new CartState(cart(List.of(item("SKU-A", "BOOKS", "9.99", 1))));
            var line = state.line(0);

            line.reduce(line.allUnits(), money("9.99"));

            EvaluationResult result = state.toResult(List.of());
            assertThat(result.finalTotal()).isEqualTo(money("0.00"));
            assertThat(result.totalDiscount()).isEqualTo(money("9.99"));
            assertInvariants(result);
        }

        @Test
        void emptyCartProducesScaleTwoZeros() {
            EvaluationResult result = new CartState(cart(List.of())).toResult(List.of());

            assertThat(result.originalSubtotal()).isEqualTo(money("0.00"));
            assertThat(result.totalDiscount()).isEqualTo(money("0.00"));
            assertThat(result.finalTotal()).isEqualTo(money("0.00"));
            assertThat(result.itemizedBreakdown()).isEmpty();
            assertThat(result.auditTrail()).isEmpty();
        }

        private void applyBundle(LineState line, int[] units, String savings) {
            line.reduce(units, money(savings));
            line.markBundled(units);
            line.markItemDiscounted();
        }
    }

    // ---- helpers -------------------------------------------------------------

    /** The README accounting invariants that CartState is responsible for. */
    private static void assertInvariants(EvaluationResult result) {
        BigDecimal sumOriginal = Money.ZERO;
        BigDecimal sumFinal = Money.ZERO;
        BigDecimal sumDiscount = Money.ZERO;
        for (ItemizedLine line : result.itemizedBreakdown()) {
            assertThat(line.originalExtendedPrice().scale()).isEqualTo(2);
            assertThat(line.discount().scale()).isEqualTo(2);
            assertThat(line.finalExtendedPrice().scale()).isEqualTo(2);
            assertThat(line.finalExtendedPrice().signum()).isGreaterThanOrEqualTo(0);
            assertThat(line.discount())
                    .isEqualTo(line.originalExtendedPrice().subtract(line.finalExtendedPrice()));
            sumOriginal = sumOriginal.add(line.originalExtendedPrice());
            sumFinal = sumFinal.add(line.finalExtendedPrice());
            sumDiscount = sumDiscount.add(line.discount());
        }
        assertThat(result.originalSubtotal()).isEqualTo(sumOriginal);
        assertThat(result.finalTotal()).isEqualTo(sumFinal);
        assertThat(result.finalTotal().signum()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalDiscount()).isEqualTo(result.originalSubtotal().subtract(result.finalTotal()));
        assertThat(result.totalDiscount()).isEqualTo(sumDiscount);
    }

    private static List<BigDecimal> unitPrices(LineState line) {
        return IntStream.range(0, line.quantity()).mapToObj(line::currentUnitPrice).toList();
    }

    private static CartItem item(String sku, String category, String unitPrice, int quantity) {
        return new CartItem(sku, category, new BigDecimal(unitPrice), quantity);
    }

    private static Cart cart(List<CartItem> items) {
        return new Cart("cart-1", "INR", Instant.parse("2026-03-15T10:00:00Z"), UserTier.STANDARD, items, null, null);
    }

    private static AuditEntry audit(String ruleId, RuleType type, String amount) {
        return new AuditEntry(ruleId, ruleId, type, RuleStatus.APPLIED, money(amount), "Applied: test");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
