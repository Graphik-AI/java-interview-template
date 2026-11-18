package com.company.promoengine.api;

import com.company.promoengine.api.dto.EvaluateCartRequest;
import com.company.promoengine.api.dto.EvaluateCartResponse;
import com.company.promoengine.engine.PromotionEngine;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartEvaluationController {

    private final PromotionEngine promotionEngine;

    public CartEvaluationController(PromotionEngine promotionEngine) {
        this.promotionEngine = promotionEngine;
    }

    @PostMapping("/evaluate")
    public EvaluateCartResponse evaluate(@Valid @RequestBody EvaluateCartRequest request) {
        var cart = EvaluationMapper.toCart(request.cart());
        var rules = request.promotions().stream().map(EvaluationMapper::toRule).toList();
        return EvaluationMapper.toResponse(promotionEngine.evaluate(cart, rules));
    }
}
