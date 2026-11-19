# Dynamic Pricing & Promo Engine

**Senior Backend Engineering Take-Home (2-Hour Timebox)**

You are given a working Spring Boot application with a stubbed pricing engine. Implement the stateless promotion engine behind `POST /api/v1/cart/evaluate` so that it satisfies this specification and passes the test suite.

## Interview Expectations

- **Timebox:** 2 hours.
- **AI assistants:** Allowed. You own every line you submit—keep the design clean enough to walk through and extend live in a **30-minute follow-up** (where you may be asked to add a new rule type without AI).
- **How we evaluate:** Passing the fixtures is the baseline. We look closely at:
  - **Extensibility & design:** Clean separation between rule handlers, cart/unit state, and money allocation—not one giant `switch` block.
  - **Correctness under rule interactions:** Bundle leftovers, mix-and-match unit splits, spend-tier downgrades on a running subtotal, non-stackable cart discounts, per-line rounding, and one-cent remainders.
  - **Your own tests:** Targeted tests covering edge cases that the 6 public fixtures do not exercise.
- **No external dependencies:** No database, network calls, or Docker. Java 21 and the included Maven Wrapper are all you need.

---

## Quick Start

```bash
./mvnw test              # fast feedback loop
./mvnw spring-boot:run   # starts on http://localhost:8080
```

When calling `POST /api/v1/cart/evaluate` manually, send the fixture's `request` object (`{ "cart": ..., "promotions": ... }`), not the outer fixture envelope.

### What Is Already Built

- **API & validation:** Request/response DTOs, input normalization (`EvaluationMapper`), and RFC 7807 `ProblemDetail` HTTP 400 responses for malformed JSON and bean-validation failures.
- **Engine seam:** `PromotionEngine` and a placeholder `DefaultPromotionEngine` that prices each line at `Money.extended(unitPrice, quantity)` and emits a placeholder `SKIPPED` entry per rule.
- **Tests:** `CartEvaluationWebTest` (undiscounted cart, empty cart, HTTP 400s—keep enabled) and `PromoEngineFixtureTest` (6 public fixtures in `src/test/resources/fixtures/public`, currently `@Disabled`).

### Deliverables

1. Replace the stub in `DefaultPromotionEngine` (using package-private types under `com.company.promoengine.engine`). Keep a **single `PromotionEngine` Spring bean**.
2. Remove `@Disabled` from `PromoEngineFixtureTest` and make `./mvnw test` pass. **Do not edit the public fixture files.**
3. Add your own tests for edge cases beyond the 6 public fixtures.
4. Fill in `DECISIONS.md` (a few sentences per heading).

### Project Layout

```text
com.company.promoengine
  api/        CartEvaluationController, EvaluationMapper, dto/
  domain/     Cart, CartItem, PromotionRule, RuleType, EvaluationResult, AuditEntry, Money
  engine/     PromotionEngine, DefaultPromotionEngine   <-- implement here
  exception/  GlobalExceptionHandler (RFC 7807 ProblemDetail)
```

Stack: Java 21, Spring Boot 3.5.16, Maven. Money uses `BigDecimal` (scale 2, `HALF_UP`). Timestamps use `Instant`.

---

## How the Engine Works

The engine runs a **single deterministic sequential pass**—do **not** search for an optimal or cheaper subset of rules.

1. **Initialize unit state:** Each cart line represents `quantity` units starting at `unitPrice`, with `originalExtendedPrice = Money.extended(unitPrice, quantity)`. A line's current price is the sum of its units' current prices.
2. **Sort promotions once:**
   - `priority` ascending (`1` runs before `10`)
   - `id` ascending lexicographically (`String.compareTo`, so `PROMO_A` runs before `PROMO_Z`)
   - original request index (stable tie-breaker)
3. **Evaluate each rule once:** Run the gatekeeper checks in order, apply any discount immediately to the running cart state, and record **exactly one** `AuditEntry` (`APPLIED` or `SKIPPED`) in evaluation order.
4. **Build response:** Keep `itemizedBreakdown` in original request order and satisfy all scale-2 accounting invariants.

### Four Core Mechanics

1. **Unit-level tracking (bundles & mix-and-match)**
   - A `BUNDLE_FIXED_PRICE` rule consumes complete component sets and marks those units as **bundled**. Later item-level rules skip bundled units, while leftover units on the same line stay eligible at their current price.
   - *Example:* 3 laptops, 2 mice, and 2 bags form 2 bundles. The 2 bundled laptops are excluded from later item rules, while the 1 leftover laptop still qualifies for a later 10% `LAPTOP` category rule.
   - `BUY_X_GET_CHEAPEST_FREE` discounts individual free units on a multi-quantity line rather than discounting the entire line.

2. **Running subtotal & `stackableWithItemPromos`**
   - Applied rules reduce affected prices immediately, so later rules always evaluate against the **running subtotal** rather than the original subtotal (e.g., an item discount can drop a cart below a higher spend tier, and two 10% category rules on `100.00` grant `10.00` then `9.00`).
   - Once an item-level rule lowers a line's current price, that line is considered **item-discounted**.
   - Cart-level rules with `stackableWithItemPromos: false` apply **only** to lines that are not item-discounted. When `stackableWithItemPromos` is `true` or `null`, the rule applies to all lines.

3. **Exclusive groups**
   - A non-blank `exclusiveGroup` is claimed **only when a rule is `APPLIED`**. A `SKIPPED` rule (out of window, invalid config, failed predicate, or `0.00` discount) never blocks later rules in the same group.

4. **Rounding & penny allocation**
   - **Percentages:** Round once to scale 2 (`HALF_UP`) at the end of each line calculation (for category rules) or cart calculation (for cart rules). Do not compute a category percentage on the combined sum of multiple lines (e.g., two `1.25` lines at 15% round independently to `0.19` and `0.19`).
   - **Pro-rata penny allocation:** Used whenever a single total discount must be split across multiple lines (**cart-level grants**, **bundle savings**, and **capped category discounts**). Allocate the discount in integer cents proportionally to each eligible line's weight using the **largest-remainder (Hamilton) method**: assign each line its integer-cent floor share, then distribute leftover cents one at a time to the eligible lines with the largest fractional remainders whose share is still below their weight, breaking ties in favor of the **earlier line in request order**.
   - *Example:* Splitting `10.00` across three equal lines produces `3.34`, `3.33`, `3.33`.
   - **Zero floor:** A grant never exceeds its eligible base, so neither a line nor the cart total can drop below `0.00`.

---

## Per-Rule Gatekeeper & Validation

For each rule in sorted order, stop at the first matching outcome and emit one `AuditEntry`:

1. **Inverted window:** `validFrom` is after `validTo` → `SKIPPED`
2. **Validity window:** `evaluationTimestamp` is outside inclusive `[validFrom, validTo]` → `SKIPPED`
3. **Exclusive group:** non-blank `exclusiveGroup` is already claimed → `SKIPPED`
4. **Configuration:** missing or illegal fields for this rule type → `SKIPPED` (not an HTTP 400)
5. **Predicate:** rule condition is not satisfied (no matching items/units, incomplete bundle, coupon/payment mismatch, no qualifying tier, empty eligible base) → `SKIPPED`
6. **Zero discount:** computed discount is `0.00` → `SKIPPED`
7. **Apply:** reduce affected prices immediately, claim non-blank `exclusiveGroup`, emit `APPLIED`

Every `AuditEntry` must include a clear, non-blank human-readable `reason` explaining why the rule was applied or skipped (and never the placeholder stub string). The fixture tests validate exact prices, line splits, rule order, `status`, and `discountAmount`, and assert that `reason` is non-blank rather than matching exact wording.

### Configuration Validation Rules (Step 4)

- `percentage`: `> 0` and `<= 100`
- `buyQuantity`, `freeQuantity`: integers `>= 1`
- `bundlePrice`, tier `minSubtotal`: `>= 0`
- `amount`, `maxDiscountAmount`: `> 0`
- `tiers`: non-empty; every tier has `minSubtotal >= 0` and **exactly one** of valid `percentage` or valid `amount`
- `components`: non-empty; every component has a non-blank `sku` and `quantity >= 1`, and each `sku` appears at most once
- Required fields per rule type (`category`, `skus`, `couponCode`) must be non-blank / non-empty (with no blank entries in `skus`)

---

## Rule Specifications (6 Types)

### Item-Level Rules (3)

#### 1. `PERCENTAGE_OFF_CATEGORY`
- **Inputs:** `conditions.category`, `discountConfig.percentage`, optional `discountConfig.maxDiscountAmount`.
- **Behavior:**
  - Match lines in `conditions.category` (case-sensitive) that have non-bundled units.
  - For each matching line independently, compute `percentage%` of that line's non-bundled current price (rounded once, scale 2 `HALF_UP`, capped at the line's eligible base).
  - If `maxDiscountAmount` is set and the sum of line discounts exceeds it, cap the rule total at `maxDiscountAmount` and allocate that cap across the matching lines via penny allocation weighted by each line's uncapped discount.

#### 2. `BUY_X_GET_CHEAPEST_FREE`
- **Inputs:** `conditions.skus`, `discountConfig.buyQuantity`, `discountConfig.freeQuantity`.
- **Behavior:**
  - Pool all non-bundled units whose `sku` is in `conditions.skus` (case-sensitive). Works for both single-SKU and multi-SKU pools.
  - Every complete group of `buyQuantity + freeQuantity` units earns `freeQuantity` free units (e.g., buy 2 get 1: 1–2 units → 0 free, 3–5 → 1 free, 6–8 → 2 free).
  - Sort pooled units by current unit price ascending, then line order ascending, then unit index within the line ascending.
  - Free the first `freeUnits` units at their full current unit price. Each affected line's discount is the sum of its freed units' prices.

#### 3. `BUNDLE_FIXED_PRICE`
- **Inputs:** `conditions.components` (`{ sku, quantity }`), `discountConfig.bundlePrice`.
- **Behavior:**
  - Form as many complete sets as the cart supports (`min(floor(lineQuantity / requiredQuantity))` across components; a missing SKU forms 0 sets).
  - Per-bundle savings equal the sum of component `unitPrice * requiredQuantity` minus `bundlePrice`.
  - Allocate total savings (`saveEach * bundleCount`) across the component lines via penny allocation weighted by each component's consumed value (`unitPrice * requiredQuantity * bundleCount`).
  - Mark the `requiredQuantity * bundleCount` consumed units on each component line as **bundled**; leftover units stay unbundled and eligible for later item-level rules.

---

### Cart-Level Rules (3)

All three cart-level rules follow the same flow:
1. Select eligible lines: all lines if `stackableWithItemPromos` is `true` or `null`, or only non-item-discounted lines if `false`.
2. Compute `base` = sum of current prices of those eligible lines.
3. Compute the cart grant (always capped at `base`).
4. Allocate the grant across eligible lines via penny allocation weighted by each line's current price, and reduce their current prices immediately.

#### 4. `TIERED_SPEND`
- **Inputs:** `discountConfig.tiers` (each with `minSubtotal` and either `percentage` or `amount`).
- **Behavior:** Select the single tier with the **greatest `minSubtotal <= base`** regardless of list order (if two tiers share the same `minSubtotal`, pick the earlier one in the list). A percentage tier grants `percentage%` of `base` (rounded to scale 2, capped at `base`); a fixed tier grants `min(amount, base)`.

#### 5. `CART_PERCENTAGE_OFF`
- **Inputs:** `discountConfig.percentage`.
- **Behavior:** Grants `percentage%` of `base` (rounded to scale 2, capped at `base`). Skipped when no eligible subtotal remains (`base == 0.00`).

#### 6. `COUPON_PAYMENT_OFF`
- **Inputs:** `conditions.couponCode`, optional `conditions.paymentMethod`, and `discountConfig.percentage` or `discountConfig.amount`.
- **Behavior:** Match `cart.couponCode` case-insensitively. If the rule sets `conditions.paymentMethod`, also match `cart.paymentMethod` case-insensitively; otherwise ignore the cart's payment method. Applies the percentage or fixed amount against `base`, capped at `base`.

---

## API Contract & Accounting Invariants

### Request Normalization (Handled in `EvaluationMapper`)

- `currency`: 3-letter uppercase code echoed on the response (no currency conversion).
- `evaluationTimestamp`: ISO-8601 `Instant`.
- `userTier`: `STANDARD` or `VIP` (available on `Cart`; none of the 6 baseline rules filter on it yet).
- `items` and `promotions`: required, may be empty. Empty `promotions` returns original line prices and an empty `auditTrail`.
- Blank `couponCode`, `paymentMethod`, and `exclusiveGroup` normalize to `null`.
- Money fields (`unitPrice`, `amount`, `maxDiscountAmount`, `bundlePrice`, tier `minSubtotal`/`amount`) are scaled to 2 decimal places on input; `percentage` stays unscaled (`10` means 10%).
- Category and SKU matches are **case-sensitive**; coupon and payment matches are **case-insensitive**.

### Sample Response

```json
{
  "cartId": "cart-category",
  "currency": "INR",
  "originalSubtotal": 2500.00,
  "totalDiscount": 200.00,
  "finalTotal": 2300.00,
  "itemizedBreakdown": [
    {
      "sku": "SKU-SHOES",
      "category": "FOOTWEAR",
      "quantity": 1,
      "unitPrice": 2000.00,
      "originalExtendedPrice": 2000.00,
      "discount": 200.00,
      "finalExtendedPrice": 1800.00
    }
  ],
  "auditTrail": [
    {
      "ruleId": "PROMO_FOOT",
      "ruleName": "Footwear 10% off",
      "type": "PERCENTAGE_OFF_CATEGORY",
      "status": "APPLIED",
      "discountAmount": 200.00,
      "reason": "Applied: 10% off category FOOTWEAR"
    }
  ]
}
```

### Accounting Invariants (All Amounts at Scale 2)

- `itemizedBreakdown` preserves original request order; `quantity` and `unitPrice` remain the input values even when units are bundled or free.
- Each line's `discount` is the sum of all discounts taken off that line across all rules.
- Each audit entry's `discountAmount` is only what that rule granted (`0.00` when `SKIPPED`).
- `originalSubtotal == sum(originalExtendedPrice)`
- `finalTotal == sum(finalExtendedPrice)`
- `totalDiscount == originalSubtotal - finalTotal == sum(line.discount) == sum(APPLIED audit.discountAmount)`
- `finalExtendedPrice >= 0.00` and `finalTotal >= 0.00`
- `auditTrail` has one entry per input rule, in evaluation order.

### Error Responses

Malformed JSON, unknown enum values (`detail: "Malformed request body"`), and bean-validation failures (`detail: "Request validation failed"`) return HTTP 400 `application/problem+json` (already implemented in `GlobalExceptionHandler`).

---

## Public Fixtures

`PromoEngineFixtureTest` posts each file's `request` and verifies `expectedResponse` (decimal numeric equality, exact array order and field names, and non-blank `auditTrail[].reason`):

| Fixture | Scenario |
| --- | --- |
| `01_basic_category_percentage.json` | 10% off one category; other category unchanged |
| `02_buy_x_get_y_exact_multiple.json` | Buy 2 get 1 cheapest free on quantity 3 (1 unit free) |
| `03_bundle_single_complete.json` | One complete bundle with savings split across components |
| `04_tiered_spend_single_tier.json` | Single spend tier qualifies for fixed discount |
| `05_coupon_and_payment_method.json` | Matching coupon code and payment method |
| `06_expired_and_no_matching_rules.json` | Out-of-window rules and non-matching category |

## Out of Scope

Persistence, authentication, tax, shipping, currency conversion, per-user redemption limits, rejecting duplicate rule IDs, and cross-rule optimization. Quantities fit in a 32-bit `int`, and all units on a given cart line share one input `unitPrice`.
