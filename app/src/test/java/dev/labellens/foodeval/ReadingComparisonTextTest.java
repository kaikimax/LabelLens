package dev.labellens.foodeval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ingredients rule (sameText), decided by Oussama on 2026-10-24:
 *   - null = null, and an empty text counts as null ("not printed");
 *   - case is ignored ("Sucre" = "sucre");
 *   - spacing is ignored: repeated spaces, line breaks, spaces at the ends,
 *     and spaces around punctuation ("gélifiant : pectine" = "gélifiant: pectine");
 *   - everything else is strict: a missing or extra ingredient, a missing accent,
 *     a different punctuation mark, a missing final period, a different number.
 *
 * Texts are real ones from labels.json where possible (p002 peanut butter, p004 jam).
 */
class ReadingComparisonTextTest {

    static final String JAM = "Fraises, Sucre, gélifiant : Pectine de fruits et correcteur d'acidité : acide citrique.";
    static final String PEANUT = "ARACHIDES grillées a sec (90%), sucre, huile végétale hydrogénée (colza, huile de coton), sel.";

    static boolean same(String expected, String actual) {
        return new ReadingComparison().sameText("ingredients", expected, actual);
    }

    // ---- Missing text ------------------------------------------------------------------

    @Test
    void notPrinted_andNotRead_isCorrect() {
        assertThat(same(null, null)).isTrue();
    }

    @Test
    void emptyText_countsAsNotPrinted() {
        assertThat(same("", null)).isTrue();
        assertThat(same(null, "")).isTrue();
        assertThat(same("   ", null)).isTrue();
    }

    @Test
    void printed_butNotRead_isWrong() {
        assertThat(same(JAM, null)).isFalse();
        assertThat(same(JAM, "")).isFalse();
    }

    @Test
    void notPrinted_butTheAiGaveAText_isWrong() {
        assertThat(same(null, JAM)).isFalse();
    }

    // ---- Ignored differences -----------------------------------------------------------

    @Test
    void identicalText_isCorrect() {
        assertThat(same(JAM, JAM)).isTrue();
    }

    @Test
    void caseIsIgnored() {
        assertThat(same("Sucre", "sucre")).isTrue();
        assertThat(same(PEANUT, PEANUT.toLowerCase())).isTrue();   // "ARACHIDES" printed in capitals (allergen)
        assertThat(same("Émulsifiant", "émulsifiant")).isTrue();     // accented capital
    }

    @Test
    void spaceBeforeAColon_isIgnored() {
        // The day 3 case: the AI was right, the answer key's spacing differed.
        assertThat(same(JAM, JAM.replace(" : ", ": "))).isTrue();
    }

    @Test
    void spaceAfterAComma_isIgnored() {
        assertThat(same("Fraises, Sucre", "Fraises,Sucre")).isTrue();
    }

    @Test
    void spacesInsideBrackets_areIgnored() {
        assertThat(same("huile végétale (colza, huile de coton)", "huile végétale ( colza, huile de coton )")).isTrue();
    }

    @Test
    void repeatedSpacesAndLineBreaks_areIgnored() {
        assertThat(same("Fraises, Sucre, gélifiant", "Fraises,  Sucre,\ngélifiant")).isTrue();
        assertThat(same("Fraises, Sucre", "Fraises,\r\n\tSucre")).isTrue();
    }

    @Test
    void spacesAtTheEnds_areIgnored() {
        assertThat(same("Fraises, Sucre.", "  Fraises, Sucre.  ")).isTrue();
    }

    // ---- Strict differences ------------------------------------------------------------

    @Test
    void missingIngredient_onAShortList_isWrong() {
        assertThat(same(JAM, JAM.replace("gélifiant : Pectine de fruits et ", ""))).isFalse();
    }

    @Test
    void missingShortIngredient_onALongList_isWrong() {
        // "sel" is 3 letters out of ~95: a similarity threshold would let this pass. This rule doesn't.
        assertThat(same(PEANUT, PEANUT.replace(", sel.", "."))).isFalse();
    }

    @Test
    void extraIngredient_isWrong() {
        assertThat(same("Fraises, Sucre.", "Fraises, Sucre, sel.")).isFalse();
    }

    @Test
    void missingAccent_isWrong() {
        assertThat(same(JAM, JAM.replace("gélifiant", "gelifiant"))).isFalse();
    }

    @Test
    void differentPunctuation_isWrong() {
        assertThat(same("Fraises, Sucre", "Fraises; Sucre")).isFalse();
    }

    @Test
    void missingFinalPeriod_isWrong() {
        assertThat(same(JAM, JAM.substring(0, JAM.length() - 1))).isFalse();
    }

    @Test
    void differentPercentage_isWrong() {
        assertThat(same(PEANUT, PEANUT.replace("(90%)", "(80%)"))).isFalse();
    }
    
    @Test
    void spaceBeforePercent_isIgnored() {
        assertThat(same(PEANUT, PEANUT.replace("(90%)", "(90 %)"))).isTrue();
    }

    @Test
    void spaceInsideAWord_isWrong() {
        // Spacing is ignored BETWEEN words and punctuation, not inside a word: "sucre" ≠ "suc re".
        assertThat(same("Fraises, Sucre", "Fraises, Suc re")).isFalse();
    }
}