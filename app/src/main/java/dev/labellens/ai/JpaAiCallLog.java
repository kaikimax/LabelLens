package dev.labellens.ai;

import org.springframework.stereotype.Component;

/** Stores every AI call in the ai_call table. */
@Component
public class JpaAiCallLog implements AiCallLog {

    private final AiCallRepository repository;

    public JpaAiCallLog(AiCallRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(AiCallRecord call) {
        repository.save(AiCall.from(call));
    }
}
