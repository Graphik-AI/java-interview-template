package com.company.promoengine.domain;

/** One SKU and the quantity of that SKU required to form a single bundle. */
public record BundleComponent(String sku, int quantity) {
}
