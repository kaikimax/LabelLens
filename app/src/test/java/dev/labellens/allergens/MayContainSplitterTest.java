package dev.labellens.allergens;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MayContainSplitterTest {

    static MayContainSplitter.Split split(String text) {
        return MayContainSplitter.split(text);
    }

    @Test
    void frenchStatement_afterTheIngredients() {
        var s = split("Farine de blé, sucre, sel. Peut contenir des traces de fruits à coque et de sésame.");

        assertThat(s.ingredients()).isEqualTo("Farine de blé, sucre, sel.");
        assertThat(s.mayContain()).isEqualTo("Peut contenir des traces de fruits à coque et de sésame.");
    }

    @Test
    void p010_englishStatement_withNoLineBreak() {
        // Day 5: the reader appended this sentence to the ingredients 2 runs out of 3.
        var s = split("Vineger, Salt, Natural Color (E120), Antioxidant (Ascorbic Acid). May Contain Celery.");

        assertThat(s.ingredients()).isEqualTo("Vineger, Salt, Natural Color (E120), Antioxidant (Ascorbic Acid).");
        assertThat(s.mayContain()).isEqualTo("May Contain Celery.");
    }

    @Test
    void theEarliestPhraseWins() {
        // "traces de" appears inside the statement that starts with "peut contenir".
        var s = split("Cacao, sucre, peut contenir des traces de lait");

        assertThat(s.ingredients()).isEqualTo("Cacao, sucre");
        assertThat(s.mayContain()).isEqualTo("peut contenir des traces de lait");
    }

    @Test
    void factoryStatement() {
        var s = split("Sucre, cacao. Fabriqué dans un atelier qui utilise des arachides.");

        assertThat(s.ingredients()).isEqualTo("Sucre, cacao.");
        assertThat(s.mayContain()).startsWith("Fabriqué dans un atelier");
    }

    @Test
    void arabicStatement() {
        var s = split("دقيق القمح، سكر، ملح. قد يحتوي على آثار من الحليب");

        assertThat(s.ingredients()).isEqualTo("دقيق القمح، سكر، ملح.");
        assertThat(s.mayContain()).isEqualTo("قد يحتوي على آثار من الحليب");
    }

    @Test
    void noStatement_keepsEverything() {
        var s = split("  Fraises, sucre, pectine.  ");

        assertThat(s.ingredients()).isEqualTo("Fraises, sucre, pectine.");
        assertThat(s.mayContain()).isNull();
    }

    @Test
    void theStatementEndsAtItsSentence() {
        // Day 6, 8410376061789: "Sans blé. Sans oeufs." were read as traces of wheat and eggs.
        var s = split("Flocons d'avoine, sucre. Il peut contenir des traces de lait. Sans blé. Sans oeufs.");

        assertThat(s.mayContain()).isEqualTo("Il peut contenir des traces de lait.");
        assertThat(s.ingredients()).isEqualTo("Flocons d'avoine, sucre. Sans blé. Sans oeufs.");
    }

    @Test
    void severalStatements_areAllCollected() {
        // Day 6, 7610400068529: the same text in English, then in French.
        var s = split("Sugar, hazelnuts. May contain almonds. Sucre, noisettes. Peut contenir des amandes.");

        assertThat(s.mayContain()).isEqualTo("May contain almonds. Peut contenir des amandes.");
        assertThat(s.ingredients()).isEqualTo("Sugar, hazelnuts. Sucre, noisettes.");
    }

    @Test
    void presencePossible() {
        // Day 6, 3029330068143.
        var s = split("Farine de blé, sel. Présence possible de: lait, œufs, graines de sésame.");

        assertThat(s.mayContain()).isEqualTo("Présence possible de: lait, œufs, graines de sésame.");
    }

    @Test
    void decimalsAndAbbreviations_dontEndTheStatement() {
        var s = split("Sucre. Peut contenir des traces de fruits à coque (noix, amandes, etc.) et 0.5% de lait.");

        assertThat(s.mayContain()).isEqualTo("Peut contenir des traces de fruits à coque (noix, amandes, etc.) et 0.5% de lait.");
    }

    @Test
    void nullOrBlank() {
        assertThat(split(null).mayContain()).isNull();
        assertThat(split("   ").mayContain()).isNull();
    }
}
