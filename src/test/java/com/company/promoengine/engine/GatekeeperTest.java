package com.company.promoengine.engine;

import static com.company.promoengine.engine.EngineTestSupport.NOW;
import static com.company.promoengine.engine.EngineTestSupport.WINDOW_END;
import static com.company.promoengine.engine.EngineTestSupport.WINDOW_START;
import static com.company.promoengine.engine.EngineTestSupport.cart;
import static com.company.promoengine.engine.EngineTestSupport.categoryRule;
import static com.company.promoengine.engine.EngineTestSupport.evaluate;
import static com.company.promoengine.engine.EngineTestSupport.item;
import static com.company.promoengine.engine.EngineTestSupport.money;
import static com.company.promoengine.engine.EngineTestSupport.rule;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.promoengine.domain.AuditEntry;
import com.company.promoengine.domain.Cart;
import com.company.promoengine.domain.EvaluationResult;
import com.company.promoengine.domain.ItemizedLine;
import com.company.promoengine.domain.PromotionRule;
import com.company.promoengine.domain.RuleStatus;
import com.company.promoengine.domain.RuleType;
import com.company.promoengine.domain.UserTier;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Engine orchestration: rule ordering, the README gates, exclusive groups, and audit output.
 * Every evaluation goes through {@link EngineTestSupport#evaluate}, which also checks the
 * accounting invariants (one entry per rule, SKIPPED = 0.00 with a non-blank reason, ...).
 */
class GatekeeperTest {

    private static final Cart BOOKS_100 = cart(item("SKU-BOOK", "BOOKS", "100.00", 1));

    @Nested
    class Ordering {

        @Test
        void lowerPriorityNumberRunsFirst() {
            var result = evaluate(BOOKS_100,
                    categoryRule("PROMO_B", "BOOKS", "50").priority(2).build(),
                    categoryRule("PROMO_A", "BOOKS", "10").priority(1).build());

            // 10% of 100.00, then 50% of the running 90.00
            assertAudit(result, 0, "PROMO_A", RuleStatus.APPLIED, "10.00");
            assertAudit(result, 1, "PROMO_B", RuleStatus.APPLIED, "45.00");
            assertThat(result.finalTotal()).isEqualTo(money("45.00"));
        }

        @Test
        void priorityWinsOverId() {
            var result = evaluate(BOOKS_100,
                    categoryRule("PROMO_A", "BOOKS", "50").priority(2).build(),
                    categoryRule("PROMO_Z", "BOOKS", "10").priority(1).build());

            assertThat(result.auditTrail()).extracting(AuditEntry::ruleId).containsExactly("PROMO_Z", "PROMO_A");
        }

        @Test
        void samePriorityIsOrderedById() {
            var result = evaluate(BOOKS_100,
                    categoryRule("PROMO_Z", "BOOKS", "50").build(),
                    categoryRule("PROMO_A", "BOOKS", "10").build());

            assertAudit(result, 0, "PROMO_A", RuleStatus.APPLIED, "10.00");
            assertAudit(result, 1, "PROMO_Z", RuleStatus.APPLIED, "45.00");
        }

        @Test
        void idsCompareLexicographicallyNotNumerically() {
            var result = evaluate(BOOKS_100,
                    categoryRule("PROMO_9", "BOOKS", "50").build(),
                    categoryRule("PROMO_10", "BOOKS", "10").build());

            // "PROMO_10" < "PROMO_9" because '1' < '9'
            assertThat(result.auditTrail()).extracting(AuditEntry::ruleId).containsExactly("PROMO_10", "PROMO_9");
        }

        @Test
        void samePriorityAndIdKeepRequestOrder() {
            PromotionRule first = categoryRule("DUP", "BOOKS", "50").name("first").build();
            PromotionRule second = categoryRule("DUP", "BOOKS", "10").name("second").build();

            var forward = evaluate(BOOKS_100, first, second);
            assertThat(forward.auditTrail()).extracting(AuditEntry::ruleName).containsExactly("first", "second");
            assertThat(forward.auditTrail()).extracting(AuditEntry::discountAmount)
                    .containsExactly(money("50.00"), money("5.00"));

            var reversed = evaluate(BOOKS_100, second, first);
            assertThat(reversed.auditTrail()).extracting(AuditEntry::ruleName).containsExactly("second", "first");
            assertThat(reversed.auditTrail()).extracting(AuditEntry::discountAmount)
                    .containsExactly(money("10.00"), money("45.00"));
        }
    }

    @Nested
    class ValidityWindow {

        @Test
        void invertedWindowIsSkipped() {
            var result = evaluate(BOOKS_100,
                    categoryRule("PROMO_INV", "BOOKS", "10").window(WINDOW_END, WINDOW_START).build());

            assertAudit(result, 0, "PROMO_INV", RuleStatus.SKIPPED, "0.00");
            assertThat(result.auditTrail().get(0).reason()).contains("inverted");
            assertThat(result.finalTotal()).isEqualTo(money("100.00"));
        }

        @Test
        void windowBoundariesAreInclusive() {
            var cart = cart(
                    item("SKU-A", "A", "100.00", 1),
                    item("SKU-B", "B", "100.00", 1),
                    item("SKU-C", "C", "100.00", 1));

            var result = evaluate(cart,
                    categoryRule("STARTS_NOW", "A", "10").window(NOW, WINDOW_END).build(),
                    categoryRule("ENDS_NOW", "B", "10").window(WINDOW_START, NOW).build(),
                    categoryRule("ONLY_NOW", "C", "10").window(NOW, NOW).build());

            assertThat(result.auditTrail()).extracting(AuditEntry::status)
                    .containsOnly(RuleStatus.APPLIED);
            assertThat(result.totalDiscount()).isEqualTo(money("30.00"));
        }

        @Test
        void oneSecondOutsideTheWindowIsSkipped() {
            Instant justAfter = NOW.plusSeconds(1);
            Instant justBefore = NOW.minusSeconds(1);

            var result = evaluate(BOOKS_100,
                    categoryRule("NOT_STARTED", "BOOKS", "10").window(justAfter, WINDOW_END).build(),
                    categoryRule("ALREADY_ENDED", "BOOKS", "10").window(WINDOW_START, justBefore).build());

            assertThat(result.auditTrail()).allSatisfy(entry -> {
                assertThat(entry.status()).isEqualTo(RuleStatus.SKIPPED);
                assertThat(entry.reason()).contains("outside validity window");
            });
        }

        @Test
        void matchesFixture06Reasons() {
            var cart = new Cart("cart-window", "INR", Instant.parse("2026-06-15T12:00:00Z"),
                    UserTier.STANDARD, List.of(item("SKU-BOOK", "BOOKS", "400.00", 1)), null, null);

            var result = evaluate(cart,
                    categoryRule("PROMO_EXPIRED", "BOOKS", "50").priority(1)
                            .window(WINDOW_START, Instant.parse("2026-06-01T00:00:00Z")).build(),
                    categoryRule("PROMO_FUTURE", "BOOKS", "50").priority(2)
                            .window(Instant.parse("2026-07-01T00:00:00Z"), WINDOW_END).build(),
                    categoryRule("PROMO_OTHER", "ELECTRONICS", "10").priority(3).build());

            assertThat(result.auditTrail()).extracting(AuditEntry::reason).containsExactly(
                    "Skipped: evaluationTimestamp 2026-06-15T12:00:00Z outside validity window"
                            + " [2026-01-01T00:00:00Z, 2026-06-01T00:00:00Z]",
                    "Skipped: evaluationTimestamp 2026-06-15T12:00:00Z outside validity window"
                            + " [2026-07-01T00:00:00Z, 2026-12-31T23:59:59Z]",
                    "Skipped: no items in category ELECTRONICS");
            assertThat(result.finalTotal()).isEqualTo(money("400.00"));
        }
    }

    @Nested
    class Configuration {

        static Stream<Arguments> invalidRules() {
            return Stream.of(
                    Arguments.of("percentage missing", categoryRule("R", "BOOKS", null).build()),
                    Arguments.of("percentage zero", categoryRule("R", "BOOKS", "0").build()),
                    Arguments.of("percentage negative", categoryRule("R", "BOOKS", "-5").build()),
                    Arguments.of("percentage above 100", categoryRule("R", "BOOKS", "100.01").build()),
                    Arguments.of("category missing", categoryRule("R", null, "10").build()),
                    Arguments.of("category blank", categoryRule("R", "  ", "10").build()),
                    Arguments.of("cap zero", categoryRule("R", "BOOKS", "10").maxDiscount("0.00").build()),
                    Arguments.of("cap negative", categoryRule("R", "BOOKS", "10").maxDiscount("-1.00").build()),
                    Arguments.of("conditions missing", categoryRule("R", "BOOKS", "10").withoutConditions().build()),
                    Arguments.of("discountConfig missing",
                            categoryRule("R", "BOOKS", "10").withoutDiscountConfig().build()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidRules")
        void invalidConfigurationIsSkippedNotThrown(String label, PromotionRule rule) {
            var result = evaluate(BOOKS_100, rule);

            assertThat(result.auditTrail().get(0).status()).isEqualTo(RuleStatus.SKIPPED);
            assertThat(result.auditTrail().get(0).reason()).startsWith("Skipped: invalid configuration: ");
            assertThat(result.finalTotal()).isEqualTo(money("100.00"));
        }
    }

    @Nested
    class PredicateAndZeroDiscount {

        @Test
        void noMatchingCategoryIsSkipped() {
            var result = evaluate(BOOKS_100, categoryRule("PROMO_EL", "ELECTRONICS", "10").build());

            assertThat(result.auditTrail().get(0).reason()).isEqualTo("Skipped: no items in category ELECTRONICS");
        }

        @Test
        void zeroComputedDiscountIsSkipped() {
            // 10% of 0.04 = 0.004, which rounds to 0.00
            var result = evaluate(cart(item("SKU-CHEAP", "CHEAP", "0.04", 1)),
                    categoryRule("PROMO_TINY", "CHEAP", "10").build());

            assertThat(result.auditTrail().get(0).status()).isEqualTo(RuleStatus.SKIPPED);
            assertThat(result.auditTrail().get(0).reason()).isEqualTo("Skipped: computed discount is 0.00");
            assertThat(result.finalTotal()).isEqualTo(money("0.04"));
        }

        // A fully bundled category is covered in CategoryPercentageHandlerTest, because no
        // bundle rule exists yet to bundle units through the engine.
    }

    @Nested
    class ExclusiveGroups {

        @Test
        void appliedRuleClaimsItsGroup() {
            var result = evaluate(BOOKS_100,
                    categoryRule("R1", "BOOKS", "10").priority(1).group("G").build(),
                    categoryRule("R2", "BOOKS", "10").priority(2).group("G").build(),
                    categoryRule("R3", "BOOKS", "10").priority(3).group("OTHER").build(),
                    categoryRule("R4", "BOOKS", "10").priority(4).build());

            assertAudit(result, 0, "R1", RuleStatus.APPLIED, "10.00");
            assertAudit(result, 1, "R2", RuleStatus.SKIPPED, "0.00");
            assertThat(result.auditTrail().get(1).reason()).isEqualTo("Skipped: exclusive group G already claimed by an earlier rule");
            assertAudit(result, 2, "R3", RuleStatus.APPLIED, "9.00");
            assertAudit(result, 3, "R4", RuleStatus.APPLIED, "8.10");
        }

        static Stream<Arguments> skippedBlockers() {
            return Stream.of(
                    Arguments.of("expired", categoryRule("BLOCKER", "BOOKS", "50")
                            .window(WINDOW_START, NOW.minusSeconds(1))),
                    Arguments.of("inverted window", categoryRule("BLOCKER", "BOOKS", "50")
                            .window(WINDOW_END, WINDOW_START)),
                    Arguments.of("invalid configuration", categoryRule("BLOCKER", "BOOKS", "0")),
                    Arguments.of("no matching category", categoryRule("BLOCKER", "ELECTRONICS", "50")),
                    Arguments.of("zero discount", categoryRule("BLOCKER", "CHEAP", "10")),
                    Arguments.of("unsupported type", rule("BLOCKER", RuleType.TIERED_SPEND)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("skippedBlockers")
        void skippedRuleDoesNotClaimItsGroup(String label, EngineTestSupport.RuleBuilder blocker) {
            var cart = cart(item("SKU-BOOK", "BOOKS", "100.00", 1), item("SKU-CHEAP", "CHEAP", "0.04", 1));

            var result = evaluate(cart,
                    blocker.priority(1).group("G").build(),
                    categoryRule("FOLLOWER", "BOOKS", "10").priority(2).group("G").build());

            assertAudit(result, 0, "BLOCKER", RuleStatus.SKIPPED, "0.00");
            assertAudit(result, 1, "FOLLOWER", RuleStatus.APPLIED, "10.00");
        }

        @Test
        void blankGroupIsIgnored() {
            var result = evaluate(BOOKS_100,
                    categoryRule("R1", "BOOKS", "10").priority(1).group("   ").build(),
                    categoryRule("R2", "BOOKS", "10").priority(2).group("   ").build(),
                    categoryRule("R3", "BOOKS", "10").priority(3).group("").build());

            assertThat(result.auditTrail()).extracting(AuditEntry::status).containsOnly(RuleStatus.APPLIED);
        }
    }

    @Nested
    class UnsupportedTypes {

        @ParameterizedTest
        @EnumSource(value = RuleType.class, mode = EnumSource.Mode.EXCLUDE,
                names = {"PERCENTAGE_OFF_CATEGORY", "BUY_X_GET_CHEAPEST_FREE"})
        void ruleTypeWithoutHandlerIsSkipped(RuleType type) {
            var result = evaluate(BOOKS_100, rule("PROMO_X", type).build());

            assertAudit(result, 0, "PROMO_X", RuleStatus.SKIPPED, "0.00");
            assertThat(result.auditTrail().get(0).reason()).isEqualTo("Skipped: rule type " + type + " is not supported yet");
        }
    }

    @Nested
    class AuditAndState {

        @Test
        void skippedRulesLeaveTheCartUnchanged() {
            var cart = cart(item("SKU-BOOK", "BOOKS", "100.00", 2), item("SKU-CHEAP", "CHEAP", "0.04", 1));
            var baseline = evaluate(cart);

            var result = evaluate(cart,
                    categoryRule("EXPIRED", "BOOKS", "50").window(WINDOW_START, NOW.minusSeconds(1)).build(),
                    categoryRule("INVERTED", "BOOKS", "50").window(WINDOW_END, WINDOW_START).build(),
                    categoryRule("INVALID", "BOOKS", "150").build(),
                    categoryRule("NO_MATCH", "ELECTRONICS", "50").build(),
                    categoryRule("ZERO", "CHEAP", "10").build(),
                    rule("UNSUPPORTED", RuleType.CART_PERCENTAGE_OFF).build());

            assertThat(result.itemizedBreakdown()).isEqualTo(baseline.itemizedBreakdown());
            assertThat(result.totalDiscount()).isEqualTo(money("0.00"));
            assertThat(result.auditTrail()).extracting(AuditEntry::status).containsOnly(RuleStatus.SKIPPED);
        }

        @Test
        void auditEntryCarriesRuleIdentityAndPrefixedReason() {
            var result = evaluate(BOOKS_100,
                    categoryRule("PROMO_BOOKS", "BOOKS", "10").name("Books 10% off").build(),
                    categoryRule("PROMO_EL", "ELECTRONICS", "10").name("Electronics 10% off").priority(2).build());

            assertThat(result.auditTrail()).containsExactly(
                    new AuditEntry("PROMO_BOOKS", "Books 10% off", RuleType.PERCENTAGE_OFF_CATEGORY,
                            RuleStatus.APPLIED, money("10.00"), "Applied: 10% off category BOOKS"),
                    new AuditEntry("PROMO_EL", "Electronics 10% off", RuleType.PERCENTAGE_OFF_CATEGORY,
                            RuleStatus.SKIPPED, money("0.00"), "Skipped: no items in category ELECTRONICS"));
        }

        @Test
        void noPromotionsGivesEmptyAuditAndOriginalPrices() {
            var result = evaluate(BOOKS_100);

            assertThat(result.auditTrail()).isEmpty();
            assertThat(result.finalTotal()).isEqualTo(money("100.00"));
        }

        @Test
        void itemizedBreakdownKeepsRequestLineOrder() {
            var cart = cart(
                    item("SKU-C", "TOYS", "10.00", 1),
                    item("SKU-A", "BOOKS", "20.00", 1),
                    item("SKU-B", "GAMES", "30.00", 1));

            var result = evaluate(cart, categoryRule("PROMO_BOOKS", "BOOKS", "10").build());

            assertThat(result.itemizedBreakdown()).extracting(ItemizedLine::sku)
                    .containsExactly("SKU-C", "SKU-A", "SKU-B");
            assertThat(result.itemizedBreakdown()).extracting(ItemizedLine::discount)
                    .containsExactly(money("0.00"), money("2.00"), money("0.00"));
        }
    }

    private static void assertAudit(EvaluationResult result, int index, String ruleId, RuleStatus status, String amount) {
        AuditEntry entry = result.auditTrail().get(index);
        assertThat(entry.ruleId()).isEqualTo(ruleId);
        assertThat(entry.status()).isEqualTo(status);
        assertThat(entry.discountAmount()).isEqualTo(money(amount));
    }
}
