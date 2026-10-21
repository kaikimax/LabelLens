package dev.labellens.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Row of the ai_call table (V3__create_ai_call.sql). */
@Entity
@Table(name = "ai_call")
public class AiCall {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(length = 100)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private boolean success;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiCall() {
    }

    static AiCall from(AiCallRecord r) {
        AiCall call = new AiCall();
        call.id = UUID.randomUUID();
        call.productId = r.productId();
        call.purpose = r.purpose();
        call.model = r.model();
        call.inputTokens = r.inputTokens();
        call.outputTokens = r.outputTokens();
        call.durationMs = r.durationMs();
        call.success = r.success();
        call.error = r.error() == null || r.error().length() <= MAX_ERROR_LENGTH
                ? r.error()
                : r.error().substring(0, MAX_ERROR_LENGTH);
        call.createdAt = Instant.now();
        return call;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getPurpose() {
        return purpose;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public boolean isSuccess() {
        return success;
    }
}
