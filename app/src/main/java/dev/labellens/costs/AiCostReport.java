package dev.labellens.costs;

import java.util.List;

/**
 * What GET /api/ai-costs returns: AI calls grouped by step (purpose) and model, then totals.
 *
 * Costs are estimates from token counts and list prices (ModelPrices). costUsd is null for a model
 * with no known price; such models are listed in unpricedModels and left out of the totals' cost.
 */
public record AiCostReport(
        String note,
        List<Row> byStepAndModel,
        List<Step> perProduct,
        Totals totals,
        List<String> unpricedModels
) {

    /**
     * @param calls           every call, failed ones included (a failed call can still cost tokens)
     * @param avgDurationMs   average time of one call
     */
    public record Row(
            String purpose,
            String model,
            long calls,
            long failedCalls,
            long inputTokens,
            long outputTokens,
            Double costUsd,
            long avgDurationMs,
            long maxDurationMs
    ) {
    }

    /**
     * What one product costs in one step, all models and retries of that step together.
     *
     * @param products          distinct products that went through this step
     * @param costPerProduct    the step's priced cost / products
     * @param secondsPerProduct the step's total call time / products
     */
    public record Step(String purpose, long products, double costPerProduct, double secondsPerProduct) {
    }

    /**
     * Per-product figures add up the steps (perProduct), NOT total cost / all products: products
     * uploaded before a step existed (day 3 uploads have no allergen call) would otherwise make a
     * product look cheaper than it is today.
     *
     * @param costUsd               everything so far
     * @param products              distinct products with at least one AI call
     * @param costPer1000Products   1,000 x the sum of the steps' cost per product
     * @param avgAiSecondsPerProduct the sum of the steps' seconds per product: waiting time for AI
     */
    public record Totals(
            long calls,
            long failedCalls,
            double costUsd,
            long products,
            Double costPer1000Products,
            Double avgAiSecondsPerProduct
    ) {
    }
}