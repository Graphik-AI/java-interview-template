# Interviewer Guide

`main` gitignores `evaluation-hidden/`. `eval/promo-hidden` (fixtures `01`–`08`) and `eval/30min-extension` (fixtures `01`–`18`) track it. Copy this folder onto a candidate checkout of `main` only for evaluation and the follow-up.

`JsonComparison` checks exact totals, line splits, rule order, `status`, and `discountAmount`, while requiring `auditTrail[].reason` only to be a non-blank string (not the placeholder stub). Every failure therefore points to a real pricing, ordering, or status defect—not a wording diff.

## Run Hidden Suite

From the candidate repo root:

```bash
cp -R /path/to/evaluation-hidden ./evaluation-hidden
cp evaluation-hidden/HiddenEvaluationTest.java \
  src/test/java/com/company/promoengine/api/HiddenEvaluationTest.java
./mvnw test -Dtest=HiddenEvaluationTest
```

Stdout prints `PASS` or `FAIL` and the JSON path of the first mismatch. Delete the copied `HiddenEvaluationTest.java` before committing anything. `evaluation-hidden/` stays untracked on `main`.

## What a Failure Means (`01`–`08`)

| Fixture | What it catches |
| --- | --- |
| `01_mix_match_multi_quantity_unit_split` | Did they free one unit of the qty-3 cheap SKU, or discount that whole line? |
| `02_partial_multi_bundle_with_leftovers` | How many bundles form from 3/2/2, and does the later category percent hit only the leftover laptop? |
| `03_cascading_tier_downgrade` | After the item promo drops 5150.00 to 4850.00, did they pick the highest qualifying tier (`2000.00`), not the first/last tier or the `5000.00` tier? |
| `04_non_stackable_cart_promo_partial_cart` | With `stackableWithItemPromos: false`, is the cart percent limited to the line that never took an item promo? |
| `05_prorata_penny_reconciliation` | Largest-remainder penny split: three equal lines and `10.00` must be `3.34`, `3.33`, `3.33`, and line finals must sum to the cart final. |
| `06_exclusive_group_and_tiebreak_order` | Same priority breaks on `id` ascending (`String.compareTo`), not request order. Winner claims the group; others are skipped. |
| `07_max_cap_and_zero_floor_clamping` | Did `maxDiscountAmount` cap the category percentage at `15.00`, and did the `100.00` spend tier stop at the remaining `65.00` so the total floors at `0.00`? |
| `08_case_insensitive_coupon_and_boundary_timestamps` | Does `save20` match `SAVE20`, and are `validFrom` and `validTo` both inclusive? |

A wrong audit `discountAmount` with a right cart total usually means the cap, tier selection, or rule order is wrong even though the zero floor hid it. A right total with a wrong line split points to penny allocation or bundle allocation.

---

## Senior Scoring Rubric (AI-Assisted Round)

Passing the hidden fixtures is the **baseline**, not the whole signal. Because AI assistants can scaffold the rule logic quickly, differentiate candidates on four dimensions:

| Dimension | Strong Senior Signal | Weak / Prompt-and-Pray Signal |
| --- | --- | --- |
| **Architecture & Extensibility** | Clean separation between rule handlers (strategy/registry per `RuleType`), mutable unit-ledger state, and pro-rata penny allocator. Adding a new rule type touches one new handler class. | One 400+ line `switch` inside `DefaultPromotionEngine`, duplicated cart-allocation math across rules, or leaking mutable state. |
| **Correctness & Edge Cases** | Passes hidden fixtures cleanly; handles `BigDecimal` scale/rounding intentionally, unit-level bundling vs. leftovers, and zero-floor clamping without drift. | Fails interaction fixtures (`02`, `03`, `05`, `15`, `16`, `18`) or uses `double`/floating point. |
| **Self-Authored Tests** | Wrote focused unit/integration tests for scenarios absent from the 6 public fixtures (e.g., penny remainders, bundle leftovers, per-line rounding, exclusive group tie-breaking, invalid rule configs). | Only enabled `PromoEngineFixtureTest` or added a trivial happy-path test. |
| **Ownership & AI Judgment** | Can trace every line of their own code, explain rounding/allocation invariants from first principles, and accurately describe a place they corrected AI output in `DECISIONS.md`. | Cannot explain their own penny allocator or unit ledger without asking an LLM. |

---

## 30-Minute Live Follow-Up Script

Keep AI **off** (or restricted to syntax lookup only) during the live coding portion.

### Part 1: Code Walkthrough & Probing (10 min)
1. Pick 1–2 tricky fixtures (`02_partial_multi_bundle_with_leftovers` and `05_prorata_penny_reconciliation` or `18_cart_priority_before_item_promo`) and ask the candidate to trace their code line-by-line by hand.
2. Ask: *"What happens if a cart-level rule runs at priority 1 before an item-level rule at priority 2 on a multi-quantity line?"*
3. Ask: *"Why did you round per-line vs. once on the cart, and how does your code guarantee `sum(finalExtendedPrice) == finalTotal`?"*

### Part 2: Live Extension Exercise (20 min, no AI)
Use **`cart.userTier` (already on the `Cart` record)** to test true extensibility:

> **Prompt:** Add a 7th rule type, `VIP_TIER_PERCENTAGE_OFF`:
> - Cart-level rule requiring `discountConfig.percentage` (`0 < p <= 100`).
> - Applies only when `cart.userTier() == UserTier.VIP`; otherwise `SKIPPED`.
> - Respects `stackableWithItemPromos`, `exclusiveGroup`, validity window, running subtotal, and pro-rata penny allocation.
> - Write one test where a `VIP` rule runs at priority 1 before a category percentage rule at priority 2, and one where a `STANDARD` user is skipped.

**What to watch for:**
- Can they add the new rule cleanly without rewriting existing rule handlers or copy-pasting the cart-allocation loop?
- Do they validate `percentage` bounds and wire into the existing gatekeeper pipeline cleanly?
- Do they write a crisp test verifying both the `VIP` and `STANDARD` paths?
