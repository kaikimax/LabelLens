package dev.labellens.costs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiCostController {

    private final AiCostService service;

    public AiCostController(AiCostService service) {
        this.service = service;
    }

    /** Total cost and average time of AI calls, by step and model (SPEC section 7). */
    @GetMapping("/api/ai-costs")
    public AiCostReport costs() {
        return service.report();
    }
}
