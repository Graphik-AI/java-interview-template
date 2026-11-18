package com.company.promoengine.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.promoengine.support.JsonComparison;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CartEvaluationWebTest {

    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pricesAnUndiscountedCart() throws Exception {
        var request = """
                {
                  "cart": {
                    "cartId": "cart-smoke",
                    "currency": "INR",
                    "evaluationTimestamp": "2026-03-15T10:00:00Z",
                    "userTier": "STANDARD",
                    "items": [
                      {"sku": "SKU-A", "category": "BOOKS", "unitPrice": 33.33, "quantity": 3}
                    ]
                  },
                  "promotions": []
                }
                """;
        var expected = """
                {
                  "cartId": "cart-smoke",
                  "currency": "INR",
                  "originalSubtotal": 99.99,
                  "totalDiscount": 0.00,
                  "finalTotal": 99.99,
                  "itemizedBreakdown": [
                    {
                      "sku": "SKU-A",
                      "category": "BOOKS",
                      "quantity": 3,
                      "unitPrice": 33.33,
                      "originalExtendedPrice": 99.99,
                      "discount": 0.00,
                      "finalExtendedPrice": 99.99
                    }
                  ],
                  "auditTrail": []
                }
                """;
        assertEvaluation(request, expected);
    }

    @Test
    void emptyCartWithNoPromotionsStaysZero() throws Exception {
        var request = """
                {
                  "cart": {
                    "cartId": "cart-empty-live",
                    "currency": "USD",
                    "evaluationTimestamp": "2026-03-15T10:00:00Z",
                    "userTier": "VIP",
                    "items": []
                  },
                  "promotions": []
                }
                """;
        var expected = """
                {
                  "cartId": "cart-empty-live",
                  "currency": "USD",
                  "originalSubtotal": 0.00,
                  "totalDiscount": 0.00,
                  "finalTotal": 0.00,
                  "itemizedBreakdown": [],
                  "auditTrail": []
                }
                """;
        assertEvaluation(request, expected);
    }

    @Test
    void rejectsInvalidCart() throws Exception {
        var request = """
                {
                  "cart": {
                    "cartId": "",
                    "currency": "inr",
                    "evaluationTimestamp": "2026-03-15T10:00:00Z",
                    "userTier": "STANDARD",
                    "items": [
                      {"sku": "SKU-A", "category": "BOOKS", "unitPrice": -1.00, "quantity": 0}
                    ]
                  },
                  "promotions": []
                }
                """;
        mockMvc.perform(post("/api/v1/cart/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/cart/evaluate"))
                .andExpect(jsonPath("$.errors.length()").value(greaterThan(0)));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/cart/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Malformed request body"))
                .andExpect(jsonPath("$.instance").value("/api/v1/cart/evaluate"));
    }

    private void assertEvaluation(String request, String expected) throws Exception {
        var result = mockMvc.perform(post("/api/v1/cart/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        var actual = json.readTree(result.getResponse().getContentAsString());
        JsonComparison.assertEquals(json.readTree(expected), actual);
    }
}
