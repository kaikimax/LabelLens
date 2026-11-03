package dev.labellens.allergens;

import dev.labellens.ai.AiCallRecord;
import dev.labellens.ai.FakeTextModel;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AllergenDetectorTest {

    final FakeTextModel model = new FakeTextModel();
    final List<AiCallRecord> calls = new ArrayList<>();
    final AllergenDetector detector = new AllergenDetector(
            new AiAllergenFinder(new ClassPathResource("prompts/find-allergens.md")), model, calls::add);
    final UUID product = UUID.randomUUID();

    @Test
    void contains_isWordListAndAi_combined() {
        // A made-up AI answer with an allergen the word list can't find in this text: the union keeps both.
        model.reply("{\"allergens\": [\"en:gluten\", \"en:milk\"]}");

        AllergenOutcome o = detector.detect(product, "Lait entier, sucre.", null);

        assertThat(o.contains()).containsExactlyInAnyOrder("en:milk", "en:gluten");
        assertThat(o.foundByWordList()).containsExactly("en:milk");
        assertThat(o.foundByAi()).containsExactlyInAnyOrder("en:gluten", "en:milk");
        assertThat(o.aiChecked()).isTrue();
        assertThat(o.aiProblem()).isNull();
    }

    @Test
    void anAllergenOnlyTheWordListFinds_isStillReported() {
        model.reply(FakeTextModel.NO_ALLERGENS);

        assertThat(detector.detect(product, "Lait entier, sucre.", null).contains()).containsExactly("en:milk");
    }

    @Test
    void mayContain_comesFromThePrintedSentence_andTheAiNeverSeesIt() {
        AllergenOutcome o = detector.detect(product, "Sucre, cacao.", "Peut contenir des traces de lait et de noisettes.");

        assertThat(o.mayContain()).containsExactlyInAnyOrder("en:milk", "en:nuts");
        assertThat(o.contains()).isEmpty();
        assertThat(model.receivedTexts()).containsExactly("Sucre, cacao.");
    }

    @Test
    void aSentenceTheReaderLeftInTheIngredients_isSplitOffToo() {
        AllergenOutcome o = detector.detect(product, "Sucre, cacao. Peut contenir des traces de lait.", null);

        assertThat(o.mayContain()).containsExactly("en:milk");
        assertThat(o.contains()).isEmpty();
        assertThat(model.receivedTexts()).containsExactly("Sucre, cacao.");
    }

    @Test
    void aiFailure_keepsTheWordListResult_andSaysTheAiDidntCheck() {
        model.failWith(new IllegalStateException("Connection refused"));

        AllergenOutcome o = detector.detect(product, "Lait entier, sucre.", null);

        assertThat(o.contains()).containsExactly("en:milk");
        assertThat(o.aiChecked()).isFalse();
        assertThat(o.foundByAi()).isNull();
        assertThat(o.aiProblem()).contains("Connection refused");
        assertThat(calls).singleElement().satisfies(c -> {
            assertThat(c.purpose()).isEqualTo("FIND_ALLERGENS");
            assertThat(c.success()).isFalse();
        });
    }

    @Test
    void unusableAiAnswer_isTreatedLikeAFailure() {
        model.reply("I think there is milk.");

        AllergenOutcome o = detector.detect(product, "Lait entier, sucre.", null);

        assertThat(o.contains()).containsExactly("en:milk");
        assertThat(o.aiChecked()).isFalse();
        assertThat(o.aiProblem()).startsWith("Unusable AI answer");
        assertThat(calls).singleElement().satisfies(c -> assertThat(c.success()).isFalse());
    }

    @Test
    void noIngredientsRead_meansNotChecked_notNoAllergens() {
        AllergenOutcome o = detector.detect(product, null, "May contain nuts.");

        assertThat(o.ingredientsRead()).isFalse();
        assertThat(o.aiChecked()).isFalse();
        assertThat(o.contains()).isEmpty();
        assertThat(o.mayContain()).containsExactly("en:nuts");
        assertThat(calls).isEmpty();                              // no AI call for nothing
    }

    @Test
    void everyAiCall_isRecordedWithTheProduct() {
        detector.detect(product, "Sucre.", null);

        assertThat(calls).singleElement().satisfies(c -> {
            assertThat(c.productId()).isEqualTo(product);
            assertThat(c.purpose()).isEqualTo("FIND_ALLERGENS");
            assertThat(c.success()).isTrue();
            assertThat(c.inputTokens()).isEqualTo(200);
        });
    }
}
