package dev.labellens.foodeval;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FineTuneExamplesTest {

    @Test
    void input_isTheIngredientsPartOnly_likeInTheEval() {
        assertThat(FineTuneExamples.input("Farine de blé, sucre. Peut contenir des traces de lait."))
                .isEqualTo("Farine de blé, sucre.");
        assertThat(FineTuneExamples.input("Peut contenir des traces de lait.")).isNull();
    }

    @Test
    void agree_ignoresOrder_butNotContent() {
        assertThat(FineTuneExamples.agree(Set.of("en:milk", "en:gluten"), Set.of("en:gluten", "en:milk"))).isTrue();
        assertThat(FineTuneExamples.agree(Set.of(), Set.of())).isTrue();
        assertThat(FineTuneExamples.agree(Set.of("en:milk"), Set.of("en:milk", "en:soybeans"))).isFalse();
    }

    @Test
    void answer_isSortedCompactJson_thatTheFinderCanParse() {
        assertThat(FineTuneExamples.answer(Set.of("en:milk", "en:gluten")))
                .isEqualTo("{\"allergens\":[\"en:gluten\",\"en:milk\"]}");
        assertThat(FineTuneExamples.answer(Set.of())).isEqualTo("{\"allergens\":[]}");
    }

    @Test
    void line_hasSystemUserAssistant_inAzureFormat() {
        JsonNode line = JsonMapper.builder().build()
                .readTree(FineTuneExamples.line("INSTR", "lait, sucre", Set.of("en:milk")));

        assertThat(line.get("messages")).hasSize(3);
        assertThat(line.get("messages").get(0).get("role").asString()).isEqualTo("system");
        assertThat(line.get("messages").get(0).get("content").asString()).isEqualTo("INSTR");
        assertThat(line.get("messages").get(1).get("content").asString()).isEqualTo("lait, sucre");
        assertThat(line.get("messages").get(2).get("content").asString()).isEqualTo("{\"allergens\":[\"en:milk\"]}");
    }

    @Test
    void validation_isAboutTenPercent_andStable() {
        long count = IntStream.range(0, 3000).mapToObj(i -> "611" + i).filter(FineTuneExamples::isValidation).count();
        assertThat(count).isBetween(220L, 380L);
        assertThat(FineTuneExamples.isValidation("6111242100664")).isEqualTo(FineTuneExamples.isValidation("6111242100664"));
    }
}
