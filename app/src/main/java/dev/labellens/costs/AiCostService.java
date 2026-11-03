package dev.labellens.costs;

import dev.labellens.ai.AiCallRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Builds the cost report from the ai_call table: the database adds up, Java prices. */
@Service
public class AiCostService {

    static final String NOTE = "Estimated from token counts at list prices (US$, Azure Global Standard). "
            + "Includes failed calls. Eval runs are not stored here.";

    private final AiCallRepository calls;

    public AiCostService(AiCallRepository calls) {
        this.calls = calls;
    }

    @Transactional(readOnly = true)
    public AiCostReport report() {
        List<AiCostReport.Row> rows = new ArrayList<>();
        TreeSet<String> unpriced = new TreeSet<>();
        long totalCalls = 0;
        long totalFailed = 0;
        double totalCost = 0;
        Map<String, Double> costByStep = new HashMap<>();
        Map<String, Long> durationByStep = new HashMap<>();

        for (AiCallRepository.Summary s : calls.summarize()) {
            Optional<ModelPrices.Price> price = ModelPrices.of(s.getModel());
            Double cost = price.map(p -> p.cost(s.getInputTokens(), s.getOutputTokens())).orElse(null);
            if (cost == null) {
                unpriced.add(s.getModel() == null ? "(unknown)" : s.getModel());
            } else {
                totalCost += cost;
                costByStep.merge(s.getPurpose(), cost, Double::sum);
            }
            durationByStep.merge(s.getPurpose(), s.getTotalDurationMs(), Long::sum);
            rows.add(new AiCostReport.Row(s.getPurpose(), s.getModel(), s.getCalls(), s.getFailedCalls(),
                    s.getInputTokens(), s.getOutputTokens(), cost == null ? null : round(cost, 6),
                    Math.round(s.getAvgDurationMs()), s.getMaxDurationMs()));
            totalCalls += s.getCalls();
            totalFailed += s.getFailedCalls();
        }

        List<AiCostReport.Step> steps = new ArrayList<>();
        double costPerProduct = 0;
        double secondsPerProduct = 0;
        for (AiCallRepository.StepProducts sp : calls.productsPerStep()) {
            if (sp.getProducts() == 0) {
                continue;
            }
            double cost = costByStep.getOrDefault(sp.getPurpose(), 0.0) / sp.getProducts();
            double seconds = durationByStep.getOrDefault(sp.getPurpose(), 0L) / 1000.0 / sp.getProducts();
            steps.add(new AiCostReport.Step(sp.getPurpose(), sp.getProducts(), round(cost, 6), round(seconds, 1)));
            costPerProduct += cost;
            secondsPerProduct += seconds;
        }

        long products = calls.countProductsWithCalls();
        AiCostReport.Totals totals = new AiCostReport.Totals(
                totalCalls,
                totalFailed,
                round(totalCost, 6),
                products,
                steps.isEmpty() ? null : round(costPerProduct * 1000, 4),
                steps.isEmpty() ? null : round(secondsPerProduct, 1));
        return new AiCostReport(NOTE, rows, steps, totals, List.copyOf(unpriced));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}