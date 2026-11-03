package dev.labellens.costs;

import dev.labellens.ai.AiCallRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCostServiceTest {

    record Summary(String getPurpose, String getModel, long getCalls, long getFailedCalls,
                   long getInputTokens, long getOutputTokens, double getAvgDurationMs,
                   long getMaxDurationMs, long getTotalDurationMs) implements AiCallRepository.Summary {
    }

    record StepProducts(String getPurpose, long getProducts) implements AiCallRepository.StepProducts {
    }

    final AiCallRepository repository = mock(AiCallRepository.class);
    final AiCostService service = new AiCostService(repository);

    @Test
    void costsAndTimes_perRow_andPerProduct() {
        when(repository.summarize()).thenReturn(List.of(
                // 10 readings: 50,000 in + 2,500 out at gpt-4.1-mini = 0.02 + 0.004 = $0.024
                new Summary("READ_LABEL", "gpt-4.1-mini-2025-04-14", 10, 1, 50_000, 2_500, 11_400.4, 30_000, 114_004),
                // 9 allergen calls: 1,800 in + 108 out at gpt-4.1 = 0.0036 + 0.000864 = $0.004464
                new Summary("FIND_ALLERGENS", "gpt-4.1-2025-04-14", 9, 0, 1_800, 108, 2_300, 4_000, 20_700)));
        when(repository.countProductsWithCalls()).thenReturn(12L);
        // 2 old products were uploaded before the allergen step existed: 12 products in all,
        // 10 read, 9 checked for allergens.
        when(repository.productsPerStep()).thenReturn(List.of(
                new StepProducts("READ_LABEL", 10), new StepProducts("FIND_ALLERGENS", 9)));

        AiCostReport report = service.report();

        assertThat(report.byStepAndModel()).hasSize(2);
        AiCostReport.Row reading = report.byStepAndModel().getFirst();
        assertThat(reading.costUsd()).isEqualTo(0.024);
        assertThat(reading.avgDurationMs()).isEqualTo(11_400);
        assertThat(report.totals().calls()).isEqualTo(19);
        assertThat(report.totals().failedCalls()).isEqualTo(1);
        assertThat(report.totals().costUsd()).isEqualTo(0.028464);
        // Per step: 0.024 / 10 + 0.004464 / 9 = 0.0024 + 0.000496 = $0.002896 per product.
        // Total cost / 12 products would say $0.002372: too cheap, the old products had fewer steps.
        assertThat(report.totals().costPer1000Products()).isEqualTo(2.896);
        assertThat(report.totals().avgAiSecondsPerProduct()).isEqualTo(13.7);   // 11.4 s + 2.3 s
        assertThat(report.perProduct()).extracting(AiCostReport.Step::purpose)
                .containsExactlyInAnyOrder("READ_LABEL", "FIND_ALLERGENS");
        assertThat(report.unpricedModels()).isEmpty();
    }

    @Test
    void unknownModel_isListed_andNotCountedAsFree() {
        when(repository.summarize()).thenReturn(List.of(
                new Summary("READ_LABEL", "mystery-model", 3, 0, 9_000, 600, 5_000, 6_000, 15_000)));
        when(repository.countProductsWithCalls()).thenReturn(3L);
        when(repository.productsPerStep()).thenReturn(List.of(new StepProducts("READ_LABEL", 3)));

        AiCostReport report = service.report();

        assertThat(report.byStepAndModel().getFirst().costUsd()).isNull();
        assertThat(report.unpricedModels()).containsExactly("mystery-model");
        assertThat(report.totals().costUsd()).isZero();
    }

    @Test
    void noCallsYet_givesAnEmptyReport_notADivisionByZero() {
        when(repository.summarize()).thenReturn(List.of());
        when(repository.countProductsWithCalls()).thenReturn(0L);
        when(repository.productsPerStep()).thenReturn(List.of());

        AiCostReport report = service.report();

        assertThat(report.totals().calls()).isZero();
        assertThat(report.totals().costPer1000Products()).isNull();
        assertThat(report.totals().avgAiSecondsPerProduct()).isNull();
    }
}