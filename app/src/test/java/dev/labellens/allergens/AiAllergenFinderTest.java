package dev.labellens.allergens;

import dev.labellens.ai.ModelReply;
import dev.labellens.ai.TextModel;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The AI allergen step with a fake model: no network, no cost. */
class AiAllergenFinderTest {

    final AiAllergenFinder finder = new AiAllergenFinder(
            new ByteArrayResource("List the EU allergens.".getBytes(StandardCharsets.UTF_8)));

    /** Answers a fixed text, and remembers what it was sent. */
    static class FakeTextModel implements TextModel {
        final String answer;
        final List<String> received = new ArrayList<>();

        FakeTextModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ModelReply ask(String instructions, String text) {
            received.add(text);
            return new ModelReply(answer, "fake", 100, 10);
        }

        @Override
        public String name() {
            return "fake";
        }
    }

    @Test
    void aNormalAnswer() {
        var r = finder.find(new FakeTextModel("{\"allergens\": [\"en:gluten\", \"en:milk\"]}"), "Farine de blé, lait");

        assertThat(r.ok()).isTrue();
        assertThat(r.allergens()).containsExactly("en:gluten", "en:milk");
        assertThat(r.reply().inputTokens()).isEqualTo(100);
    }

    @Test
    void noAllergen_isAnEmptySet_notAFailure() {
        var r = finder.find(new FakeTextModel("{\"allergens\": []}"), "Fraises, sucre");

        assertThat(r.ok()).isTrue();
        assertThat(r.allergens()).isEmpty();
    }

    @Test
    void codeFencesAreTolerated() {
        var r = finder.find(new FakeTextModel("```json\n{\"allergens\": [\"en:eggs\"]}\n```"), "oeufs");

        assertThat(r.allergens()).containsExactly("en:eggs");
    }

    @Test
    void tagsOutsideTheFourteen_areDroppedButReported() {
        var r = finder.find(new FakeTextModel("{\"allergens\": [\"en:milk\", \"en:pork\", \"EN:Gluten\"]}"), "x");

        assertThat(r.allergens()).containsExactly("en:gluten", "en:milk");
        assertThat(r.ignored()).containsExactly("en:pork");
    }

    @Test
    void anUnusableAnswer_isAFailureWithAReason() {
        assertThat(finder.find(new FakeTextModel("The product contains milk."), "x").problem())
                .isEqualTo("the answer is not valid JSON");
        assertThat(finder.find(new FakeTextModel("{\"result\": []}"), "x").problem())
                .isEqualTo("the answer has no \"allergens\" list");
        assertThat(finder.find(new FakeTextModel(""), "x").problem())
                .isEqualTo("the answer was empty");
    }

    @Test
    void theModelReceivesTheTextAsGiven() {
        var model = new FakeTextModel("{\"allergens\": []}");
        finder.find(model, "Cacao, sucre");

        assertThat(model.received).containsExactly("Cacao, sucre");
    }
}
