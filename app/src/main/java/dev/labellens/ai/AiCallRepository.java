package dev.labellens.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AiCallRepository extends JpaRepository<AiCall, UUID> {

    List<AiCall> findByProductId(UUID productId);

    /** One row per (purpose, model). Getter names match the aliases (Spring Data interface projection). */
    interface Summary {
        String getPurpose();

        String getModel();

        long getCalls();

        long getFailedCalls();

        long getInputTokens();

        long getOutputTokens();

        double getAvgDurationMs();

        long getMaxDurationMs();

        long getTotalDurationMs();
    }

    // coalesce: token counts are null when a call failed before the model answered.
    @Query("""
            select c.purpose as purpose, c.model as model,
                   count(c) as calls,
                   sum(case when c.success = true then 0 else 1 end) as failedCalls,
                   coalesce(sum(c.inputTokens), 0) as inputTokens,
                   coalesce(sum(c.outputTokens), 0) as outputTokens,
                   avg(c.durationMs) as avgDurationMs,
                   max(c.durationMs) as maxDurationMs,
                   sum(c.durationMs) as totalDurationMs
            from AiCall c
            group by c.purpose, c.model
            order by c.purpose, c.model
            """)
    List<Summary> summarize();

    @Query("select count(distinct c.productId) from AiCall c where c.productId is not null")
    long countProductsWithCalls();

    interface StepProducts {
        String getPurpose();

        long getProducts();
    }

    /** How many products went through each step: the base for "cost of one product" per step. */
    @Query("""
            select c.purpose as purpose, count(distinct c.productId) as products
            from AiCall c
            where c.productId is not null
            group by c.purpose
            """)
    List<StepProducts> productsPerStep();
}