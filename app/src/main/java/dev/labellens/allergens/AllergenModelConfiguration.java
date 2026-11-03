package dev.labellens.allergens;

import dev.labellens.ai.TextModel;
import dev.labellens.ai.TextModels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The model AllergenDetector asks: a deployment on the same Azure resource as the reader.
 * gpt-4.1 by default (decisions log, 2026-10-31). Tests replace it with FakeTextModel.
 */
@Configuration(proxyBeanMethods = false)
class AllergenModelConfiguration {

    @Bean
    TextModel allergenModel(TextModels models, @Value("${labellens.allergens.deployment}") String deployment) {
        return models.forDeployment(deployment);
    }
}
