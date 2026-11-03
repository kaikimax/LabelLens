package dev.labellens.costs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelPricesTest {

    @Test
    void versionedNames_getTheirOwnPrice_notTheShorterPrefix() {
        // The trap: "gpt-4.1-mini-..." also starts with "gpt-4.1", which is 5x more expensive.
        assertThat(ModelPrices.of("gpt-4.1-mini-2025-04-14")).contains(new ModelPrices.Price(0.40, 1.60));
        assertThat(ModelPrices.of("gpt-4.1-nano-2025-04-14")).contains(new ModelPrices.Price(0.10, 0.40));
        assertThat(ModelPrices.of("gpt-4.1-2025-04-14")).contains(new ModelPrices.Price(2.00, 8.00));
        assertThat(ModelPrices.of("gpt-4.1")).contains(new ModelPrices.Price(2.00, 8.00));
    }

    @Test
    void fineTunedModel_getsItsBasePrice() {
        assertThat(ModelPrices.of("gpt-4.1-nano-2025-04-14.ft-0123abcd-allergens"))
                .contains(new ModelPrices.Price(0.10, 0.40));
    }

    @Test
    void unknownModels_haveNoPrice_insteadOfCostingNothing() {
        assertThat(ModelPrices.of("fake-model")).isEmpty();
        assertThat(ModelPrices.of("gpt-4.10")).isEmpty();
        assertThat(ModelPrices.of(null)).isEmpty();
    }

    @Test
    void cost_isTokensTimesPricePerMillion() {
        // A typical reading: ~5,300 in, ~250 out at gpt-4.1-mini prices.
        assertThat(new ModelPrices.Price(0.40, 1.60).cost(5_300, 250)).isCloseTo(0.00252, within(1e-9));
    }
}
