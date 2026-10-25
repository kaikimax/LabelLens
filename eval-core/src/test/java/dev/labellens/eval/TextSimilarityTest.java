package dev.labellens.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TextSimilarityTest {

    @Test
    void identicalTexts_areFullySimilar() {
        assertThat(TextSimilarity.similarity("sucre, sel", "sucre, sel")).isEqualTo(1.0);
        assertThat(TextSimilarity.similarity("", "")).isEqualTo(1.0);
    }

    @Test
    void oneEdit_overElevenCharacters() {
        assertThat(TextSimilarity.editDistance("sucre, sel", "sucre, sell")).isEqualTo(1);
        assertThat(TextSimilarity.similarity("sucre, sel", "sucre, sell")).isCloseTo(10.0 / 11, within(1e-9));
    }

    @Test
    void classicExample() {
        assertThat(TextSimilarity.editDistance("kitten", "sitting")).isEqualTo(3);
    }

    @Test
    void nulls() {
        assertThat(TextSimilarity.similarity(null, null)).isEqualTo(1.0);
        assertThat(TextSimilarity.similarity("a", null)).isEqualTo(0.0);
    }
}
