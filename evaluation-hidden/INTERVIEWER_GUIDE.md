# Interviewer guide

This folder is gitignored. Keep it outside the candidate branch and copy it in only for the follow-up. The candidate README already states every rule these fixtures check.

## Run

From the candidate repo root:

```bash
cp -R /path/to/evaluation-hidden ./evaluation-hidden
cp evaluation-hidden/HiddenEvaluationTest.java \
  src/test/java/com/company/promoengine/api/HiddenEvaluationTest.java
./mvnw test -Dtest=HiddenEvaluationTest
```

`./mvnw test` also runs it once the class is on the test source path. The public fixture test stays skipped until they remove `@Disabled`.

Stdout prints `PASS` or `FAIL` and the JSON path of the first mismatch. Delete the copied `HiddenEvaluationTest.java` before anyone commits. `evaluation-hidden/` stays untracked.

## What a failure is about

| Fixture | Ask |
| --- | --- |
| `01_mix_match_multi_quantity_unit_split` | Did they free one unit of the qty-3 cheap SKU, or discount that whole line? |
| `02_partial_multi_bundle_with_leftovers` | How many bundles form from 3/2/2, and does the later category percent hit only the leftover laptop? |
| `03_cascading_tier_downgrade` | Which tier did they pick after the item promo dropped 5150.00 to 4850.00? The highest qualifying threshold is 2000.00, not the first or last tier in the list and not the 5000.00 tier. |
| `04_non_stackable_cart_promo_partial_cart` | With `stackableWithItemPromos: false`, is the cart percent limited to the line that never took an item promo? |
| `05_prorata_penny_reconciliation` | How do they place the leftover cent? Three equal lines and 10.00 must be 3.34, 3.33, 3.33, and the line finals must sum to the cart final. |
| `06_exclusive_group_and_tiebreak_order` | Same priority is `id` ascending (`String.compareTo`), not request order. The winner claims the group; the others cite that id. |
| `07_max_cap_and_zero_floor_clamping` | Did `maxDiscountAmount` cap the percentage at 15.00, and did the later fixed discount stop at the remaining 65.00 so the total is 0.00? |
| `08_case_insensitive_coupon_and_boundary_timestamps` | Does `save20` match `SAVE20`, are `validFrom` and `validTo` inclusive, and is the VIP rule skipped for `STANDARD`? |

A wrong audit `discountAmount` with a right cart total usually means the cap, the tier, or the per-rule split is wrong even though the floor hid it. A right total with a wrong line split is the penny or the bundle allocation.
