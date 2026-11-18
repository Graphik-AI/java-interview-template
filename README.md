# Dynamic Pricing & Promo Engine

2-hour senior backend take-home. You receive a working Spring Boot application and a stubbed pricing engine. Implement the engine so `POST /api/v1/cart/evaluate` matches this contract and the JSON fixtures.

AI coding assistants are allowed. Keep the design small enough to explain and extend in a 30-minute follow-up. Be ready to walk a cart through bundle leftovers, mix-and-match units, spend-tier selection, non-stackable cart discounts, and a one-cent remainder.

There is no database, network, or Docker dependency. `./mvnw test` is the feedback loop.

## Run

```bash
./mvnw test
./mvnw spring-boot:run
```

Java 21 and the Maven Wrapper are enough. The app listens on port 8080. The body is the fixture's `request` object (`cart` and `promotions`), not the fixture envelope.

## Already implemented

- Request and response records, validation, and RFC 7807 errors for malformed JSON and bean-validation failures.
- `PromotionEngine` plus `DefaultPromotionEngine`. The stub extends each line with `Money.extended`, returns that amount as both original and final, and writes one `SKIPPED` audit row per input rule with reason `Skipped: promotion engine not implemented`. Replace that body. The stub reason is not part of the contract.
- `CartEvaluationWebTest` covers an undiscounted cart, an empty cart, and HTTP 400s. Those tests stay enabled.
- `PromoEngineFixtureTest` loads every file in `src/test/resources/fixtures/public`. It is `@Disabled` until the engine is real. Remove that annotation. Do not edit those fixture files.

The six fixtures are representative. This document is the contract, including behavior those files do not exercise. Add your own tests for the cases you implement beyond them.

Leave a single `PromotionEngine` bean. Put new rule types behind `DefaultPromotionEngine`, not a second engine bean.

## API

`POST /api/v1/cart/evaluate`

`currency` is a 3-letter uppercase code echoed on the response. The service does not convert currencies. `evaluationTimestamp` is an ISO-8601 instant. `userTier` is `STANDARD` or `VIP`. `items` and `promotions` are required and may be empty.

Optional cart fields:

- `couponCode` — compared to coupon rules with `String.equalsIgnoreCase`. Blank becomes null.
- `paymentMethod` — compared the same way, and only when the rule sets a payment method.

Category and SKU matches are case-sensitive. A blank `exclusiveGroup` is stored as null and does not exclude anything. `stackableWithItemPromos` is read by cart-level rules: null and `true` both stack; `false` does not.

Money fields (`unitPrice`, `minSubtotal`, `amount`, `maxDiscountAmount`, `bundlePrice`, tier amounts) are normalized to scale 2 on the way in. `percentage` stays a percent: `10` means 10%, not `0.10`.

### Rule types

| Type | Level | Required inputs | Effect |
| --- | --- | --- | --- |
| `PERCENTAGE_OFF_CATEGORY` | Item | `conditions.category`, `discountConfig.percentage` | That percent off the current price of non-bundled units in the category. Optional `maxDiscountAmount` caps the rule total. |
| `BUY_X_GET_Y_FREE` | Item | `conditions.sku`, `buyQuantity`, `freeQuantity` | On that SKU, `freeUnits = count / (buy + free) * free` using integer division. |
| `BUY_X_GET_CHEAPEST_FREE` | Item | `conditions.skus`, `buyQuantity`, `freeQuantity` | Same free-unit count across the pool, applied to the cheapest units. |
| `BUNDLE_FIXED_PRICE` | Item | `conditions.components`, `discountConfig.bundlePrice` | Each complete set of components is repriced to `bundlePrice`. Leftover units stay at `unitPrice`. |
| `TIERED_SPEND` | Cart | `discountConfig.tiers` | The single tier with the greatest `minSubtotal` that is still `<=` the cart base. |
| `COUPON_PAYMENT_OFF` | Cart | `conditions.couponCode`, and `percentage` or `amount` | Cart discount when the coupon matches. `paymentMethod` is checked only when the rule sets one. |
| `CART_PERCENTAGE_OFF` | Cart | `discountConfig.percentage` | That percent off the cart base. |
| `CART_THRESHOLD_FIXED_OFF` | Cart | `conditions.minSubtotal`, `discountConfig.amount` | Flat amount off the cart base when `base >= minSubtotal`. |
| `VIP_TIER_PERCENTAGE_OFF` | Cart | `discountConfig.percentage` | That percent off the cart base when `userTier` is `VIP`. |

A percentage is valid when it is greater than 0 and at most 100. `buyQuantity` and `freeQuantity` are valid when both are integers of at least 1. `minSubtotal` and `bundlePrice` are valid when they are at least 0. `amount` and `maxDiscountAmount` are valid when they are greater than 0. A tier sets `minSubtotal` and exactly one of `percentage` or `amount`. A bundle component sets a non-blank SKU and a quantity of at least 1, and each SKU appears once. Anything else, a blank category or SKU, an empty `skus` or `components` or `tiers` list, or a window with `validFrom` after `validTo`, is an invalid configuration.

Buy 2 get 1 (`buyQuantity = 2`, `freeQuantity = 1`):

| Eligible units | Free units |
| --- | --- |
| 1–2 | 0 |
| 3–5 | 1 |
| 6–8 | 2 |

### Response

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

Lines stay in request order. `quantity` and `unitPrice` are the inputs, including when only some units were bundled or given free. `discount` on a line is everything every applied rule took off that line. `discountAmount` on an audit row is only what that rule granted. `status` is `APPLIED` or `SKIPPED`.

Identities, all at scale 2:

- `originalSubtotal` = sum of `originalExtendedPrice`
- `finalTotal` = sum of `finalExtendedPrice`
- `totalDiscount` = `originalSubtotal - finalTotal` = sum of line discounts
- sum of `APPLIED` audit amounts = `totalDiscount`, and every `SKIPPED` amount is `0.00`
- `finalExtendedPrice` and `finalTotal` are never below `0.00`
- `auditTrail` has one entry per input rule, in evaluation order

### Errors

Malformed JSON and bean-validation failures return HTTP 400 `application/problem+json`:

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

A promotion that is well-formed but illegal for its type is not a 400. Skip it and audit the reason. Unknown enum values and broken JSON are 400s (`detail` is `Malformed request body`).

## Evaluation algorithm

Apply this pipeline only. Do not search for a cheaper subset of rules.

### Unit ledger

Each line is `quantity` units. Every unit starts at `unitPrice`. `originalExtendedPrice = Money.extended(unitPrice, quantity)`. The line's current price is the sum of its units' current prices.

A bundle marks the units it consumes. Later item-level rules skip bundled units. Those units already carry the bundle savings. Leftover units on the same line stay eligible and stay at `unitPrice` until a later rule changes them.

A line is item-discounted once an item-level rule has lowered its current price. Cart-level rules with `stackableWithItemPromos: false` use only lines that are not item-discounted. Every other cart-level rule uses every line.

Item-level types are the four in the table above. The other five are cart-level.

### Order

Sort rules by ascending `priority`, then ascending `id` using `String.compareTo` (lexicographic, not numeric), then original request index. Priority `1` runs before `10`. `PROMO_A` runs before `PROMO_Z` when the priority is equal, whichever was listed first.

Remember which exclusive groups have been claimed, and the id of the rule that claimed each one. A group is claimed only when a rule in it is `APPLIED`.

### Checks

For each rule, append exactly one audit entry and stop at the first matching outcome.

1. **Inverted window.** `validFrom` is after `validTo` → `Skipped: invalid configuration for {TYPE}`.
2. **Validity window.** The timestamp is outside the inclusive range `[validFrom, validTo]` → `Skipped: evaluationTimestamp {ts} outside validity window [{from}, {to}]`. Print instants with `Instant.toString()`.
3. **Exclusive group.** A non-blank group is already claimed → `Skipped: conflicts with already applied rule {id} in exclusiveGroup '{group}'`. `{group}` is in single quotes.
4. **Configuration.** Missing or illegal fields → `Skipped: invalid configuration for {TYPE}`.
5. **Predicate.** Coupon, payment method, VIP, threshold, tier, matching units, or bundle count. A failed predicate does not claim the group.
6. **Zero result.** Computed discount is `0.00` → `Skipped: calculated discount is 0.00`.
7. **Apply.** Grant the discount, reduce the affected units immediately, claim a non-blank group, and audit `APPLIED`.

`{TYPE}` is the enum name. Money inside a reason is `Money.format` (`2880.00`). A percentage is `stripTrailingZeros().toPlainString()` (`10`, `5`, `10.5`).

The cart base used in a reason is the base that rule actually priced. Call it the running subtotal in the templates below.

### Discounts

Round a percentage once, at the end of each line or cart calculation:

```text
base.multiply(percentage).divide(Money.ONE_HUNDRED, Money.SCALE, Money.ROUNDING)
```

For a category rule, `base` is the sum of current prices of non-bundled units in that category on that line. Do not percentage the sum of several lines and then split it. Two lines of `1.25` at 15% are `0.19` and `0.19`. Cap each line discount at that line's eligible base before subtracting.

`maxDiscountAmount`, when set on a category rule, caps the sum of those line discounts. If the uncapped sum is greater, the granted amount is the cap and the line discounts are replaced by allocating the cap in proportion to the uncapped line discounts. The reason gains a suffix:

`Applied: {percentage}% off category {category} (capped at maxDiscountAmount {max})`

Without a cap, or when the cap is not reached:

`Applied: {percentage}% off category {category}`

A second category percentage sees the already reduced unit price. Two 10% rules on a `100.00` line grant `10.00` and then `9.00`.

| Situation | Reason |
| --- | --- |
| No line in that category | `Skipped: no items in category {category}` |
| Buy-X-get-Y grants free units | `Applied: buy {buy} get {free} free on {sku} ({n} unit free)` or `units` when `n != 1` |
| SKU absent | `Skipped: no items with sku {sku}` |
| SKU present, zero free units | `Skipped: quantity {qty} of {sku} does not qualify for buy {buy} get {free} free` |
| Mix-and-match grants free units | `Applied: buy {buy} get {free} cheapest free ({n} unit free)` or `units` when `n != 1` |
| Mix-and-match has no eligible units | `Skipped: no eligible units for mix-and-match` |
| Mix-and-match quantity does not qualify | `Skipped: eligible quantity {qty} does not qualify for buy {buy} get {free} cheapest free` |
| Bundle forms one or more sets | `Applied: {count} x bundle at {price} saving {saveEach} on component value {componentSum}` |
| Bundle cannot be formed | `Skipped: no complete bundle can be formed` |
| Caller is not `VIP` | `Skipped: userTier {tier} is not VIP` |
| Coupon does not match | `Skipped: cart coupon does not match {ruleCode}` |
| Payment method does not match | `Skipped: payment method does not match {method}` |

`{qty}` for a same-SKU rule is that line's non-bundled quantity. `{ruleCode}` and `{method}` are the rule's configured values.

Coupon and payment comparisons use `equalsIgnoreCase`. A rule with no `paymentMethod` does not look at the cart's payment method. Applied coupon reasons:

- percentage, no payment method: `Applied: {percentage}% coupon {code} off cart (running subtotal {base})`
- percentage with a payment method: `Applied: {percentage}% coupon {code} via {method} off cart (running subtotal {base})`
- amount, no payment method: `Applied: {grant} coupon {code} off cart (running subtotal {base})`
- amount with a payment method: `Applied: {grant} coupon {code} via {method} off cart (running subtotal {base})`

`{code}` and `{method}` are the rule's values, not the cart's casing.

### Buy X get Y, and cheapest free

Same-SKU free units come off that line's non-bundled units. The discount is:

```text
eligibleExtended.multiply(BigDecimal.valueOf(freeUnits))
    .divide(BigDecimal.valueOf(eligibleCount), Money.SCALE, Money.ROUNDING)
```

Mix-and-match pools non-bundled units whose SKU is in `conditions.skus`. Sort them by current unit price ascending, then by line order, then by index within the line. `freeUnits` uses the pool size and the same integer formula. The first `freeUnits` in that order are free for their full current unit price. A line of quantity 3 loses only the units that were selected, not the whole line. The line discount is the sum of those unit prices.

### Bundles

`bundleCount` is the minimum of `floor(lineQuantity / requiredQuantity)` across components. A missing SKU contributes 0.

`componentSum` is the sum of `unitPrice * requiredQuantity` for one set. `saveEach = componentSum - bundlePrice`. Total savings are `saveEach * bundleCount`, allocated across the consumed value of each component (`unitPrice * requiredQuantity * bundleCount`) with the penny method below. Consumed units are then bundled. Leftover units are `quantity - requiredQuantity * bundleCount` and remain eligible for later item-level rules.

A later category percentage therefore prices only the leftover units. It does not reprice units already inside a bundle.

### Cart base, tiers, and thresholds

The base is the sum of current line prices, restricted to lines that are not item-discounted when `stackableWithItemPromos` is `false`.

`CART_PERCENTAGE_OFF` grants `base * percentage / 100` at scale 2, capped at `base`.

- stacking: `Applied: {percentage}% off cart (running subtotal {base})`
- not stacking: `Applied: {percentage}% off undiscounted cart subtotal {base}`
- not stacking and the base is `0.00`: `Skipped: no undiscounted items remain for a non-stackable cart promotion`

`CART_THRESHOLD_FIXED_OFF` qualifies when `base >= minSubtotal`. The grant is `min(amount, base)`.

- full amount: `Applied: {grant} off cart (running subtotal {base} >= threshold {threshold})`
- capped: `Applied: {grant} off cart (configured {amount} capped at running subtotal {base})`
- below the threshold: `Skipped: running subtotal {base} below threshold {threshold}`

`TIERED_SPEND` selects the tier with the greatest `minSubtotal` that is `<= base`. List position does not matter. Equal thresholds use the earlier tier in the list. A percentage tier uses the percentage formula on `base`. An amount tier grants `min(amount, base)`.

- percentage: `Applied: spend tier >= {min} grants {percentage}% off cart (running subtotal {base})`
- amount: `Applied: spend tier >= {min} grants {amount} off cart (running subtotal {base})`
- none qualify: `Skipped: running subtotal {base} below the lowest spend tier {lowest}`

`VIP_TIER_PERCENTAGE_OFF` uses the same percentage formula on the base:

`Applied: {percentage}% VIP tier discount on running subtotal {base}`

An item-level rule that runs first can drop the base under a higher tier. The tier rule then selects from the tiers that still qualify. It does not keep a tier the original subtotal would have reached.

### Penny allocation

Cart-level grants, bundle savings, and a `maxDiscountAmount` cap are spread in cents.

Let `D` be the discount in cents and `S` the sum of the eligible line weights in cents. For each line, in request order:

```text
base = (D * weight) / S          // integer division
fraction = (D * weight) % S
```

`leftover = D - sum(base)`. Give one leftover cent at a time to the line with the largest fraction whose share is still below its weight. Equal fractions go to the earlier line. Repeat until the leftover is gone. `D <= S`, so this finishes.

Three equal lines and `D = 10.00` produce `3.34`, `3.33`, and `3.33`. The sum of the line shares is the granted discount, and the sum of `finalExtendedPrice` is `finalTotal`.

A one-line cart receives the whole grant on that line. A grant is never larger than the base, so a line and the cart both stop at `0.00` instead of going negative.

## Fixtures

| File | What it locks |
| --- | --- |
| `fixtures/public/01_basic_category_percentage.json` | 10% off one category. A second category is unchanged. |
| `fixtures/public/02_buy_x_get_y_exact_multiple.json` | Buy 2 get 1 on quantity 3. One unit is free. |
| `fixtures/public/03_bundle_single_complete.json` | One complete bundle. Savings are split across its components. |
| `fixtures/public/04_tiered_spend_single_tier.json` | The only tier qualifies and grants its fixed amount. |
| `fixtures/public/05_coupon_and_payment_method.json` | Coupon and payment method both match. |
| `fixtures/public/06_expired_and_no_matching_rules.json` | Out-of-window rules and a category that matches nothing. Prices stay put. |

`description` in each file is a note. The test posts `request` and compares `expectedResponse`. Numbers compare as decimals. Object field order does not matter. Array order does. Extra or missing response fields fail the test.

An empty `promotions` list leaves every line at its original extended price and returns an empty audit trail.

## Deliverables

- `DefaultPromotionEngine` (and package-private types under `engine`) passing `./mvnw test` with `PromoEngineFixtureTest` enabled.
- Your own tests for an edge case the public fixtures do not cover.
- `DECISIONS.md` filled in.

## Layout

```text
com.company.promoengine
  api/            CartEvaluationController, EvaluationMapper, dto/
  domain/         Cart, CartItem, PromotionRule, RuleType, EvaluationResult, AuditEntry, Money
  engine/         PromotionEngine, DefaultPromotionEngine
  exception/      GlobalExceptionHandler (RFC 7807 ProblemDetail)
```

## Out of scope

Persistence, authentication, tax, shipping, currency conversion, per-user redemption limits, and rejecting duplicate rule ids. Quantities fit in a 32-bit `int`. Units on one line share one input `unitPrice`. Do not optimize across rules.

Stack: Java 21, Spring Boot 3.5.16, Maven. Money is `BigDecimal`. Timestamps are `Instant`.
