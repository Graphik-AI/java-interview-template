# Dynamic Pricing & Promo Engine

**Senior Backend Engineering Take-Home (2-Hour Timebox)**

You are given a working Spring Boot application with a stubbed pricing engine. Your task is to implement the promotion evaluation engine behind `POST /api/v1/cart/evaluate` so that it satisfies this specification and passes the test suite.

## Interview Expectations

- **Timebox:** 2 hours. Focus on correctness, clean domain boundaries, and readability rather than over-engineering.
- **AI assistants:** Allowed. You own every line you submit—keep the design small and clear enough to walk through and extend live in a **30-minute follow-up**.
- **Follow-up discussion:** Be ready to trace a cart through bundle leftovers, mix-and-match unit splits, spend-tier downgrades on a running subtotal, non-stackable cart discounts, and a one-cent remainder.
- **No external dependencies:** No database, network calls, or Docker. Java 21 and the included Maven Wrapper are all you need.

---

## Quick Start

```bash
./mvnw test              # fast feedback loop
./mvnw spring-boot:run   # starts on http://localhost:8080
```

When calling `POST /api/v1/cart/evaluate` manually, send the fixture's `request` object (`{ "cart": ..., "promotions": ... }`), not the outer fixture envelope.

### What Is Already Built

The web layer, contracts, and test harness are done so you can spend your time on the pricing engine itself:

- **API & validation:** Request/response DTOs, input normalization (`EvaluationMapper`), and RFC 7807 `ProblemDetail` HTTP 400 responses for malformed JSON and bean-validation failures.
- **Engine seam:** `PromotionEngine` and a placeholder `DefaultPromotionEngine` that prices each line at `Money.extended(unitPrice, quantity)` and emits a placeholder `SKIPPED` entry per rule (`Skipped: promotion engine not implemented`, which is not part of the contract).
- **Smoke & contract tests:** `CartEvaluationWebTest` (undiscounted cart, empty cart, HTTP 400 cases—keep enabled) and `PromoEngineFixtureTest` (6 public fixtures in `src/test/resources/fixtures/public`, currently `@Disabled`).

### What You Deliver

1. Replace the stub in `DefaultPromotionEngine` (using package-private types under `com.company.promoengine.engine`). Keep a **single `PromotionEngine` Spring bean**—do not register a second engine bean.
2. Remove `@Disabled` from `PromoEngineFixtureTest` and make `./mvnw test` pass. **Do not edit the public fixture files.**
3. Add your own unit or integration tests covering edge cases that the 6 public fixtures do not exercise (this README is the full contract, not just those 6 files).
4. Fill in `DECISIONS.md` (a few sentences per heading).

### Project Layout

```text
com.company.promoengine
  api/        CartEvaluationController, EvaluationMapper, dto/
  domain/     Cart, CartItem, PromotionRule, RuleType, EvaluationResult, AuditEntry, Money
  engine/     PromotionEngine, DefaultPromotionEngine   <-- implement here
  exception/  GlobalExceptionHandler (RFC 7807 ProblemDetail)
```

Stack: Java 21, Spring Boot 3.5.16, Maven. Money uses `BigDecimal` (`Money.SCALE = 2`, `RoundingMode.HALF_UP`). Timestamps use `Instant`.

---

## How the Engine Works (Mental Model)

Despite having 9 rule types, the engine is a **single deterministic sequential pass**—do **not** search for an optimal or cheaper combination of rules.

1. **Initialize unit state:** Expand each cart line into `quantity` units priced at `unitPrice`. `originalExtendedPrice = Money.extended(unitPrice, quantity)`. A line's current price is the sum of its units' current prices.
2. **Sort promotions once:**
   - `priority` ascending (`1` runs before `10`)
   - `id` ascending via `String.compareTo` (lexicographic, not numeric: `PROMO_A` before `PROMO_Z`)
   - original request index (stable tie-breaker)
3. **Evaluate rules one by one:** Run each rule through the same gatekeeper checks, apply its discount immediately to the running cart state if eligible, and append **exactly one** `AuditEntry` (`APPLIED` or `SKIPPED`) in evaluation order.
4. **Emit response:** Keep `itemizedBreakdown` in the original request order and satisfy all scale-2 accounting invariants.

### Four Core Concepts

These four mechanics govern how rules interact:

1. **Unit-level tracking (bundles & mix-and-match)**
   - A `BUNDLE_FIXED_PRICE` rule consumes complete sets of component units and marks those units as **bundled**. Later item-level rules skip bundled units, while any leftover units on the same line remain eligible at their current price.
   - *Example:* 3 laptops, 2 mice, and 2 bags form 2 desk bundles. The 2 bundled laptops are locked from later item rules, while the 1 leftover laptop still receives a later 10% `LAPTOP` category discount.
   - `BUY_X_GET_CHEAPEST_FREE` frees individual units from a multi-quantity line rather than discounting the whole line.

2. **Running subtotal & `stackableWithItemPromos`**
   - Every applied rule reduces affected prices immediately, so later rules always see the updated running subtotal—not the original subtotal.
   - *Example:* If an item rule drops a `5150.00` cart to `4850.00`, a later `TIERED_SPEND` rule evaluates tiers against `4850.00` (not `5150.00`). Likewise, two cascading 10% category rules on `100.00` grant `10.00` and then `9.00`.
   - Once any item-level rule lowers a line's current price, that line is marked **item-discounted**. Cart-level rules with `stackableWithItemPromos: false` price **only** lines that are not item-discounted. When `stackableWithItemPromos` is `true` or `null`, the cart-level rule prices all lines.

3. **Exclusive groups**
   - Track claimed non-blank `exclusiveGroup` values along with the `id` of the rule that claimed each group.
   - A group is claimed **only when a rule is `APPLIED`**. A `SKIPPED` rule (expired, invalid, failed predicate, or `0.00` discount) never claims its group.

4. **Rounding & penny allocation**
   - **Percentage rounding:** Round once at the end of each line calculation (for category rules) or cart calculation (for cart rules):
     ```text
     base.multiply(percentage).divide(Money.ONE_HUNDRED, Money.SCALE, Money.ROUNDING)
     ```
   - **Pro-rata penny allocation:** Used whenever a single total discount must be split across multiple lines—specifically **cart-level discounts**, **bundle savings**, and **capped category discounts** (`maxDiscountAmount`).
     - Work in integer cents. Let `D` be the discount in cents and `S` be the sum of eligible line weights in cents (`D <= S`).
     - For each eligible line in request order:
       ```text
       base     = (D * weight) / S   // integer division
       fraction = (D * weight) % S
       ```
     - Compute `leftover = D - sum(base)`. Distribute one leftover cent at a time to the eligible line with the **largest `fraction`** whose share is still below its weight; break ties in favor of the **earlier line in request order**.
     - *Example:* Splitting `10.00` across three equal lines produces `3.34`, `3.33`, `3.33`. A single-line cart receives the entire grant on that line. Because a grant never exceeds the eligible base, neither a line nor the cart can go below `0.00`.

---

## Per-Rule Evaluation Pipeline

For every rule (in sorted order), evaluate these steps in order and **stop at the first matching outcome**, emitting one `AuditEntry`:

| Step | Condition | Status | Exact `reason` |
| --- | --- | --- | --- |
| 1 | Inverted window (`validFrom` after `validTo`) | `SKIPPED` | `Skipped: invalid configuration for {TYPE}` |
| 2 | `evaluationTimestamp` outside inclusive `[validFrom, validTo]` | `SKIPPED` | `Skipped: evaluationTimestamp {ts} outside validity window [{from}, {to}]` |
| 3 | Non-blank `exclusiveGroup` already claimed by `{id}` | `SKIPPED` | `Skipped: conflicts with already applied rule {id} in exclusiveGroup '{group}'` |
| 4 | Missing or illegal fields for this rule type | `SKIPPED` | `Skipped: invalid configuration for {TYPE}` |
| 5 | Rule predicate not met (no matching items, below threshold, wrong coupon, etc.) | `SKIPPED` | Rule-specific skip reason (see below) |
| 6 | Computed discount is `0.00` | `SKIPPED` | `Skipped: calculated discount is 0.00` |
| 7 | Discount > `0.00` | `APPLIED` | Reduce affected units immediately, claim non-blank `exclusiveGroup`, emit rule-specific applied reason |

### Reason Formatting Conventions

The fixture tests compare the full JSON response—including exact `reason` strings. Follow these formatting rules everywhere:

- `{TYPE}`: `RuleType.name()` (e.g., `PERCENTAGE_OFF_CATEGORY`).
- Money values: `Money.format(amount)` (always 2 decimal places, e.g., `2880.00`).
- Percentages: `percentage.stripTrailingZeros().toPlainString()` (e.g., `10`, `5`, `10.5`).
- Timestamps: `Instant.toString()`.
- Coupon codes and payment methods in reasons: use the **rule's** configured casing, not the cart's input casing.
- Running subtotal in cart-rule reasons: the exact `base` that rule priced.

### Configuration Validation Rules (Step 4)

A well-formed request whose rule has illegal config is **not** an HTTP 400—skip the rule at Step 4 with `Skipped: invalid configuration for {TYPE}`.

- `percentage`: `> 0` and `<= 100`
- `buyQuantity`, `freeQuantity`: integers `>= 1`
- `minSubtotal`, `bundlePrice`: `>= 0`
- `amount`, `maxDiscountAmount`: `> 0`
- Spend tier (`tiers`): list non-empty; every tier has `minSubtotal >= 0` and **exactly one** of valid `percentage` or valid `amount`
- Bundle component (`components`): list non-empty; every component has a non-blank `sku` and `quantity >= 1`, and each `sku` appears at most once
- Required string/list fields for a rule type (`category`, `sku`, `skus`, `couponCode`) must be non-blank / non-empty (and no blank entries in `skus`)

---

## Rule Specifications

All 9 rule types fall into two groups: **4 item-level rules** and **5 cart-level rules**.

### Item-Level Rules

#### 1. `PERCENTAGE_OFF_CATEGORY`
- **Inputs:** `conditions.category`, `discountConfig.percentage`, optional `discountConfig.maxDiscountAmount`.
- **Behavior:**
  - Find all lines whose `category` matches (case-sensitive) and that have non-bundled units.
  - For each matching line independently, compute `lineDiscount = round(lineEligibleBase * percentage / 100)` where `lineEligibleBase` is the sum of current prices of non-bundled units on that line. Cap `lineDiscount` at `lineEligibleBase`. Do **not** compute the percentage on the combined sum of multiple lines (e.g., two `1.25` lines at 15% yield `0.19` and `0.19`).
  - If `maxDiscountAmount` is set and `sum(lineDiscount) > maxDiscountAmount`, replace the per-line discounts by allocating `maxDiscountAmount` across those lines using penny allocation weighted by each line's uncapped `lineDiscount`.
- **Audit reasons:**
  - Applied (not capped): `Applied: {percentage}% off category {category}`
  - Applied (capped): `Applied: {percentage}% off category {category} (capped at maxDiscountAmount {max})`
  - No matching line: `Skipped: no items in category {category}`

#### 2. `BUY_X_GET_Y_FREE`
- **Inputs:** `conditions.sku`, `discountConfig.buyQuantity`, `discountConfig.freeQuantity`.
- **Behavior:**
  - Match the line with `conditions.sku` (case-sensitive). Let `eligibleCount` be its count of non-bundled units and `eligibleExtended` be the sum of their current prices.
  - Compute free units with integer division:
    ```text
    freeUnits = (eligibleCount / (buyQuantity + freeQuantity)) * freeQuantity
    ```
    *(Example — Buy 2 get 1: 1–2 units → 0 free, 3–5 units → 1 free, 6–8 units → 2 free.)*
  - If `freeUnits > 0`, the line discount is:
    ```text
    eligibleExtended.multiply(BigDecimal.valueOf(freeUnits))
        .divide(BigDecimal.valueOf(eligibleCount), Money.SCALE, Money.ROUNDING)
    ```
- **Audit reasons:**
  - Applied: `Applied: buy {buy} get {free} free on {sku} ({n} unit free)` — use `units` when `n != 1`
  - SKU absent: `Skipped: no items with sku {sku}`
  - Present, `freeUnits == 0`: `Skipped: quantity {qty} of {sku} does not qualify for buy {buy} get {free} free` (`{qty}` is the line's non-bundled quantity)

#### 3. `BUY_X_GET_CHEAPEST_FREE`
- **Inputs:** `conditions.skus`, `discountConfig.buyQuantity`, `discountConfig.freeQuantity`.
- **Behavior:**
  - Pool all non-bundled units across cart lines whose `sku` is in `conditions.skus` (case-sensitive).
  - Compute `freeUnits = (poolSize / (buyQuantity + freeQuantity)) * freeQuantity`.
  - Sort pooled units by: current unit price ascending, then line order ascending, then unit index within the line ascending.
  - The first `freeUnits` in that order become free (discounted by their full current unit price). Each affected line's discount is the sum of its selected free units' prices.
- **Audit reasons:**
  - Applied: `Applied: buy {buy} get {free} cheapest free ({n} unit free)` — use `units` when `n != 1`
  - Pool empty: `Skipped: no eligible units for mix-and-match`
  - Pool non-empty, `freeUnits == 0`: `Skipped: eligible quantity {qty} does not qualify for buy {buy} get {free} cheapest free`

#### 4. `BUNDLE_FIXED_PRICE`
- **Inputs:** `conditions.components` (list of `{ sku, quantity }`), `discountConfig.bundlePrice`.
- **Behavior:**
  - `bundleCount = min(floor(lineQuantity / requiredQuantity))` across all components (a missing SKU yields `0`).
  - If `bundleCount == 0`, skip.
  - For one bundle set: `componentSum = sum(unitPrice * requiredQuantity)` and `saveEach = componentSum - bundlePrice`.
  - Total savings = `saveEach * bundleCount`. Allocate total savings across the component lines using penny allocation weighted by each component's consumed value (`unitPrice * requiredQuantity * bundleCount`).
  - Mark the `requiredQuantity * bundleCount` consumed units on each component line as **bundled**. Leftover units (`quantity - requiredQuantity * bundleCount`) remain unbundled and eligible for later item-level rules.
- **Audit reasons:**
  - Applied: `Applied: {count} x bundle at {price} saving {saveEach} on component value {componentSum}`
  - `bundleCount == 0`: `Skipped: no complete bundle can be formed`

---

### Cart-Level Rules

All 5 cart-level rules follow the same pattern:
1. Select eligible lines: all lines if `stackableWithItemPromos` is `true` or `null`, or only lines not marked item-discounted if `stackableWithItemPromos` is `false`.
2. Let `base` = sum of current line prices of those eligible lines.
3. Compute the cart grant (always capped at `base`).
4. Allocate the grant across the eligible lines using **penny allocation** weighted by each eligible line's current price, and reduce their current prices immediately.

#### 5. `CART_PERCENTAGE_OFF`
- **Inputs:** `discountConfig.percentage`.
- **Behavior:**
  - If `stackableWithItemPromos` is `false` and `base == 0.00`, skip.
  - Otherwise grant `min(round(base * percentage / 100), base)`.
- **Audit reasons:**
  - Applied (stackable): `Applied: {percentage}% off cart (running subtotal {base})`
  - Applied (non-stackable): `Applied: {percentage}% off undiscounted cart subtotal {base}`
  - Non-stackable with `base == 0.00`: `Skipped: no undiscounted items remain for a non-stackable cart promotion`

#### 6. `CART_THRESHOLD_FIXED_OFF`
- **Inputs:** `conditions.minSubtotal`, `discountConfig.amount`.
- **Behavior:** Qualifies when `base >= minSubtotal`. Grants `min(amount, base)`.
- **Audit reasons:**
  - Applied (full amount): `Applied: {grant} off cart (running subtotal {base} >= threshold {threshold})`
  - Applied (capped at `base`): `Applied: {grant} off cart (configured {amount} capped at running subtotal {base})`
  - Below threshold: `Skipped: running subtotal {base} below threshold {threshold}`

#### 7. `TIERED_SPEND`
- **Inputs:** `discountConfig.tiers` (each with `minSubtotal` and either `percentage` or `amount`).
- **Behavior:**
  - Pick the single tier with the **greatest `minSubtotal` that is `<= base`** (regardless of list order; if two tiers share the same `minSubtotal`, pick the earlier one in the list).
  - A percentage tier grants `min(round(base * percentage / 100), base)`; an amount tier grants `min(amount, base)`.
- **Audit reasons:**
  - Applied (percentage tier): `Applied: spend tier >= {min} grants {percentage}% off cart (running subtotal {base})`
  - Applied (amount tier): `Applied: spend tier >= {min} grants {amount} off cart (running subtotal {base})`
  - No tier qualifies: `Skipped: running subtotal {base} below the lowest spend tier {lowest}`

#### 8. `COUPON_PAYMENT_OFF`
- **Inputs:** `conditions.couponCode`, optional `conditions.paymentMethod`, and `discountConfig.percentage` or `discountConfig.amount`.
- **Behavior:**
  - Match `cart.couponCode` against `conditions.couponCode` with `equalsIgnoreCase`.
  - If the rule specifies `conditions.paymentMethod`, match `cart.paymentMethod` with `equalsIgnoreCase`; otherwise ignore the cart's payment method.
  - Grant `min(round(base * percentage / 100), base)` for percentage, or `min(amount, base)` for fixed amount.
- **Audit reasons** (`{code}`, `{ruleCode}`, and `{method}` use the rule's configured values):
  - Applied (%, no payment method): `Applied: {percentage}% coupon {code} off cart (running subtotal {base})`
  - Applied (%, with payment method): `Applied: {percentage}% coupon {code} via {method} off cart (running subtotal {base})`
  - Applied (amount, no payment method): `Applied: {grant} coupon {code} off cart (running subtotal {base})`
  - Applied (amount, with payment method): `Applied: {grant} coupon {code} via {method} off cart (running subtotal {base})`
  - Coupon mismatch: `Skipped: cart coupon does not match {ruleCode}`
  - Payment method mismatch: `Skipped: payment method does not match {method}`

#### 9. `VIP_TIER_PERCENTAGE_OFF`
- **Inputs:** `discountConfig.percentage`.
- **Behavior:** Qualifies when `cart.userTier == UserTier.VIP`. Grants `min(round(base * percentage / 100), base)`.
- **Audit reasons:**
  - Applied: `Applied: {percentage}% VIP tier discount on running subtotal {base}`
  - Not VIP: `Skipped: userTier {tier} is not VIP`

---

## API Contract & Accounting Invariants

### Request Normalization (Already Handled in `EvaluationMapper`)

- `currency`: 3-letter uppercase code echoed on the response (no conversion).
- `evaluationTimestamp`: ISO-8601 `Instant`.
- `userTier`: `STANDARD` or `VIP`.
- `items` and `promotions`: required, may be empty. An empty `promotions` list leaves every line unchanged and returns an empty `auditTrail`.
- Blank `couponCode`, `paymentMethod`, and `exclusiveGroup` are normalized to `null`.
- All money fields (`unitPrice`, `minSubtotal`, `amount`, `maxDiscountAmount`, `bundlePrice`, tier `minSubtotal`/`amount`) are scaled to 2 decimal places on input. `percentage` is kept unscaled (`10` means 10%, not `0.10`).
- Category and SKU matching are **case-sensitive**; coupon code and payment method matching are **case-insensitive**.

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

### Required Invariants (All Amounts at Scale 2)

- `itemizedBreakdown` stays in original request order; `quantity` and `unitPrice` remain the input values even when units are bundled or free.
- Each line's `discount` is the sum of all discounts applied to that line across all rules.
- Each audit entry's `discountAmount` is only what that single rule granted (`0.00` when `SKIPPED`).
- `originalSubtotal == sum(originalExtendedPrice)`
- `finalTotal == sum(finalExtendedPrice)`
- `totalDiscount == originalSubtotal - finalTotal == sum(line.discount) == sum(APPLIED audit.discountAmount)`
- `finalExtendedPrice >= 0.00` and `finalTotal >= 0.00`
- `auditTrail` has one entry per input rule, in evaluation order.

### Error Responses

Malformed JSON, unknown enum values (`detail: "Malformed request body"`), and bean-validation failures (`detail: "Request validation failed"`) return HTTP 400 `application/problem+json` (already wired in `GlobalExceptionHandler`):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/cart/evaluate",
  "errors": [{ "field": "cart.cartId", "message": "must not be blank" }]
}
```

---

## Public Fixtures

`PromoEngineFixtureTest` posts each file's `request` and compares the full JSON body against `expectedResponse` (decimal numeric equality; object key order ignored; array order and field names/strings must match exactly):

| Fixture | Scenario |
| --- | --- |
| `01_basic_category_percentage.json` | 10% off one category; other category unchanged |
| `02_buy_x_get_y_exact_multiple.json` | Buy 2 get 1 on quantity 3 (1 unit free) |
| `03_bundle_single_complete.json` | One complete bundle with savings split across components |
| `04_tiered_spend_single_tier.json` | Single spend tier qualifies for fixed discount |
| `05_coupon_and_payment_method.json` | Matching coupon code and payment method |
| `06_expired_and_no_matching_rules.json` | Out-of-window rules and non-matching category |

## Out of Scope

Persistence, authentication, tax, shipping, currency conversion, per-user redemption limits, rejecting duplicate rule IDs, and cross-rule optimization. Quantities fit in a 32-bit `int`, and all units on a given cart line share one input `unitPrice`.
