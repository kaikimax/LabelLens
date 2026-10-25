package dev.labellens.reading;

import dev.labellens.ai.AiCallRecord;
import dev.labellens.ai.FakeVisionModel;
import dev.labellens.ai.ImageInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests: no Spring, no database, no network. The model is a fake,
 * and AI calls are collected in a plain list instead of the ai_call table.
 */
class LabelReaderTest {

    private static final List<ImageInput> PHOTO = List.of(new ImageInput(new byte[]{1, 2, 3}, "image/jpeg"));
    private static final UUID PRODUCT = UUID.randomUUID();

    private FakeVisionModel model;
    private List<AiCallRecord> calls;
    private LabelReader reader;

    @BeforeEach
    void setUp() {
        model = new FakeVisionModel();
        calls = new ArrayList<>();
        var prompt = new ByteArrayResource("Read the label.".getBytes(StandardCharsets.UTF_8));
        reader = new LabelReader(model, JsonMapper.builder().build(), calls::add, prompt);
    }

    @Test
    void validAnswer_isExtracted_inOneCall() {
        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.reading().energyKj()).isEqualTo(1088.4);
        assertThat(outcome.reading().sugars()).isEqualTo(60.0);
        assertThat(outcome.reading().fibre()).isNull();
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().success()).isTrue();
        assertThat(calls.getFirst().inputTokens()).isEqualTo(1200);
    }

    @Test
    void answerWrappedInCodeFences_isStillAccepted() {
        model.reply("```json\n" + FakeVisionModel.JAM_JSON + "\n```");

        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isTrue();
        assertThat(calls).hasSize(1);
    }

    @Test
    void brokenThenValid_succeedsOnSecondAttempt_andExplainsTheProblem() {
        model.reply("Sorry, I can't read this {", FakeVisionModel.JAM_JSON);

        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isTrue();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).success()).isFalse();
        assertThat(calls.get(1).success()).isTrue();
        // The second request tells the model what was wrong.
        assertThat(model.receivedInstructions().get(1)).contains("previous answer was rejected");
    }

    @Test
    void brokenTwice_fails_withReadableReason() {
        model.reply("not json", "still not json");

        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.failureReason()).contains("after 2 attempts");
        assertThat(outcome.rawResponse()).isEqualTo("still not json");
        assertThat(calls).hasSize(2).noneMatch(AiCallRecord::success);
    }

    @Test
    void validJsonWithWrongValues_isRetried() {
        String servingWithoutSize = FakeVisionModel.JAM_JSON.replace("\"basis\": \"100g\"", "\"basis\": \"serving\"");
        model.reply(servingWithoutSize, FakeVisionModel.JAM_JSON);

        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isTrue();
        assertThat(model.receivedInstructions().get(1)).contains("serving_size");
    }

    @Test
    void numberWrittenAsText_isRejected() {
        String lessThan = FakeVisionModel.JAM_JSON.replace("\"fat\": 0.3", "\"fat\": \"<0.5\"");
        model.reply(lessThan, lessThan);

        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isFalse();
    }

    @Test
    void networkError_failsImmediately_withoutRetry() {
        model.failWith(new IllegalStateException("Connection refused"));

        ReadOutcome outcome = reader.read(PRODUCT, PHOTO);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.failureReason()).contains("Connection refused");
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().success()).isFalse();
    }

    @Test
    void stripCodeFences_leavesPlainJsonUntouched() {
        assertThat(LabelReader.stripCodeFences("  {\"a\": 1}  ")).isEqualTo("{\"a\": 1}");
        assertThat(LabelReader.stripCodeFences("```json\n{\"a\": 1}\n```")).isEqualTo("{\"a\": 1}");
        assertThat(LabelReader.stripCodeFences("```\n{\"a\": 1}\n```")).isEqualTo("{\"a\": 1}");
    }

    @Test
    void aFailedCall_reportsTheCauseUnderneath() {
        // Day 5: p003 failed 3 times with only "Request failed", which explained nothing.
        var e = new RuntimeException("Request failed",
                new IllegalStateException("HTTP 400 Bad Request\nbody: content_filter"));

        assertThat(LabelReader.describe(e))
                .isEqualTo("Request failed <- IllegalStateException: HTTP 400 Bad Request");
    }
}
