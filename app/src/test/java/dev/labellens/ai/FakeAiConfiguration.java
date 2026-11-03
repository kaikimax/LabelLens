package dev.labellens.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real models with FakeVisionModel (reading) and FakeTextModel (allergens) in Spring tests.
 * @Primary: when both the real and the fake model exist, Spring injects the fake.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAiConfiguration {

    @Bean
    @Primary
    FakeVisionModel fakeVisionModel() {
        return new FakeVisionModel();
    }

    @Bean
    @Primary
    FakeTextModel fakeTextModel() {
        return new FakeTextModel();
    }
}
