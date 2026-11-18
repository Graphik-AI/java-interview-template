package com.company.promoengine.api;

import com.company.promoengine.api.dto.AuditEntryDto;
import com.company.promoengine.api.dto.BundleComponentDto;
import com.company.promoengine.api.dto.CartDto;
import com.company.promoengine.api.dto.DiscountConfigDto;
import com.company.promoengine.api.dto.EvaluateCartResponse;
import com.company.promoengine.api.dto.ItemizedLineDto;
import com.company.promoengine.api.dto.PromotionRuleDto;
import com.company.promoengine.api.dto.RuleConditionsDto;
import com.company.promoengine.api.dto.SpendTierDto;
import com.company.promoengine.domain.AuditEntry;
import com.company.promoengine.domain.BundleComponent;
import com.company.promoengine.domain.Cart;
import com.company.promoengine.domain.CartItem;
import com.company.promoengine.domain.DiscountConfig;
import com.company.promoengine.domain.EvaluationResult;
import com.company.promoengine.domain.ItemizedLine;
import com.company.promoengine.domain.Money;
import com.company.promoengine.domain.PromotionRule;
import com.company.promoengine.domain.RuleConditions;
import com.company.promoengine.domain.SpendTier;
import java.math.BigDecimal;
import java.util.List;

public final class EvaluationMapper {

    private EvaluationMapper() {
    }

    public static Cart toCart(CartDto dto) {
        var items = dto.items().stream()
                .map(item -> new CartItem(
                        item.sku(),
                        item.category(),
                        Money.of(item.unitPrice()),
                        item.quantity()))
                .toList();
        return new Cart(
                dto.cartId(),
                dto.currency(),
                dto.evaluationTimestamp(),
                dto.userTier(),
                items,
                blankToNull(dto.couponCode()),
                blankToNull(dto.paymentMethod()));
    }

    public static PromotionRule toRule(PromotionRuleDto dto) {
        return new PromotionRule(
                dto.id(),
                dto.name(),
                dto.type(),
                dto.priority(),
                blankToNull(dto.exclusiveGroup()),
                dto.stackableWithItemPromos(),
                dto.validFrom(),
                dto.validTo(),
                toConditions(dto.conditions()),
                toDiscount(dto.discountConfig()));
    }

    public static EvaluateCartResponse toResponse(EvaluationResult result) {
        return new EvaluateCartResponse(
                result.cartId(),
                result.currency(),
                result.originalSubtotal(),
                result.totalDiscount(),
                result.finalTotal(),
                result.itemizedBreakdown().stream().map(EvaluationMapper::toLine).toList(),
                result.auditTrail().stream().map(EvaluationMapper::toAudit).toList());
    }

    private static RuleConditions toConditions(RuleConditionsDto dto) {
        List<BundleComponent> components = dto.components() == null
                ? null
                : dto.components().stream()
                        .map(EvaluationMapper::toComponent)
                        .toList();
        return new RuleConditions(
                dto.category(),
                dto.sku(),
                money(dto.minSubtotal()),
                dto.skus(),
                blankToNull(dto.couponCode()),
                blankToNull(dto.paymentMethod()),
                components);
    }

    private static BundleComponent toComponent(BundleComponentDto dto) {
        return new BundleComponent(dto.sku(), dto.quantity());
    }

    private static DiscountConfig toDiscount(DiscountConfigDto dto) {
        List<SpendTier> tiers = dto.tiers() == null
                ? null
                : dto.tiers().stream().map(EvaluationMapper::toTier).toList();
        return new DiscountConfig(
                dto.percentage(),
                money(dto.amount()),
                dto.buyQuantity(),
                dto.freeQuantity(),
                money(dto.maxDiscountAmount()),
                money(dto.bundlePrice()),
                tiers);
    }

    private static SpendTier toTier(SpendTierDto dto) {
        return new SpendTier(money(dto.minSubtotal()), dto.percentage(), money(dto.amount()));
    }

    private static ItemizedLineDto toLine(ItemizedLine line) {
        return new ItemizedLineDto(
                line.sku(),
                line.category(),
                line.quantity(),
                line.unitPrice(),
                line.originalExtendedPrice(),
                line.discount(),
                line.finalExtendedPrice());
    }

    private static AuditEntryDto toAudit(AuditEntry entry) {
        return new AuditEntryDto(
                entry.ruleId(),
                entry.ruleName(),
                entry.type(),
                entry.status(),
                entry.discountAmount(),
                entry.reason());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : Money.of(value);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
