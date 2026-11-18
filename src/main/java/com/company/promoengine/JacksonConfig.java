package com.company.promoengine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Money is {@link java.math.BigDecimal} and must be written as a plain decimal
     * ({@code 2880.00}), never scientific notation. Instants stay ISO-8601 strings.
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer monetaryJson() {
        return builder -> builder
                .featuresToEnable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
