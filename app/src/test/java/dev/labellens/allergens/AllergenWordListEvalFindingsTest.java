package dev.labellens.allergens;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cases found by the day 6 eval on 500 Open Food Facts products (docs/notes/day6-allergen-eval.md).
 * Kept apart from AllergenWordListTest (written before the code) so it's clear where each case came from.
 */
class AllergenWordListEvalFindingsTest {

    static Set<String> find(String text) {
        return AllergenWordList.find(text);
    }

    // ---- Non-breaking spaces (Open Food Facts texts contain U+00A0 and U+202F) -----------

    @Test
    void aNonBreakingSpace_insideAWord_stillMatches() {
        assertThat(find("Peut contenir des traces de fruits à coque, de céréales contenant du gluten et de lait."))
                .containsExactlyInAnyOrder("en:nuts", "en:gluten", "en:milk");
    }

    @Test
    void aNonBreakingSpace_insideAnException_stillBlanksIt() {
        assertThat(find("Sucre, beurre de cacao, pâte de cacao")).isEmpty();
        assertThat(find("Sucre, beurre de cacao")).isEmpty();
    }

    // ---- Fish species ------------------------------------------------------------------

    @Test
    void fishSpecies_singularAndPlural() {
        assertThat(find("Maquereau à l'huile")).containsExactly("en:fish");
        assertThat(find("Morceaux de maquereaux, sauce tomate")).containsExactly("en:fish");
        assertThat(find("Sardines, huile d'olive vierge extra, sel.")).containsExactly("en:fish");
        assertThat(find("Mackerel fillets, salt")).containsExactly("en:fish");
    }

    @Test
    void fishInArabic_withPrefixesAndBrokenPlural() {
        assertThat(find("سمك الماكريل، زيت، ملح")).containsExactly("en:fish");
        assertThat(find("زيت، والتونة، ملح")).containsExactly("en:fish");   // "and the tuna"
        assertThat(find("طماطم بالسردين")).containsExactly("en:fish");      // "with sardines"
        assertThat(find("اسماك، ملح")).containsExactly("en:fish");           // plural of سمك
        assertThat(find("سلمون مرقط")).containsExactly("en:fish");
    }

    // ---- Traps -------------------------------------------------------------------------

    @Test
    void nutmegAndCoconut_areNotNuts() {
        assertThat(find("Sel, noix de muscade, poivre")).isEmpty();
        assertThat(find("حليب جوز الهند، سكر")).isEmpty();
        assertThat(find("جوز الهند المبشور")).isEmpty();
    }

    @Test
    void arabicPrefixes_onOtherAllergens() {
        assertThat(find("سكر، وبيض")).containsExactly("en:eggs");
        assertThat(find("مسحوق للحليب")).containsExactly("en:milk");
        assertThat(find("الفول السوداني، ملح")).containsExactly("en:peanuts");
    }

    @Test
    void arabicPrefixes_doNotMatchInsideOtherWords() {
        assertThat(find("صنع في لبنان")).isEmpty();                          // Lebanon, not لبن (milk)
    }

    // ---- Review round 2 -----------------------------------------------------------------

    @Test
    void claimsAfterTheStatement_areNotAllergens() {
        assertThat(find("Flocons d'avoine, sucre. Sans blé. Sans oeufs. Sans fruits à coque."))
                .containsExactly("en:gluten");                                   // oats only
        assertThat(find("Maïs 93%, sucre, sel. SANS LACTOSE. SANS BLÉ")).isEmpty();
        assertThat(find("lait entier sans lactose")).containsExactly("en:milk");
    }

    @Test
    void nutFlavours_areNotNuts() {
        assertThat(find("Chicorée soluble, arôme noisette")).isEmpty();
        assertThat(find("arôme naturel (goût noisette), sel")).isEmpty();
    }

    @Test
    void plantMilks_areThePlantNotMilk() {
        assertThat(find("Lait d'amandes (eau, amandes 2,8%), sucre")).containsExactly("en:nuts");
        assertThat(find("lait de soja, sucre")).containsExactly("en:soybeans");
        assertThat(find("lait de riz, huile")).isEmpty();
    }

    @Test
    void refinedSoyFat_isExempt_likeSoyOil() {
        assertThat(find("Lait de vache pasteurisé, matière grasse végétale de soja, sel")).containsExactly("en:milk");
    }

    @Test
    void dairyWords_andUnaccentedNuts() {
        assertThat(find("traces éventuelles de produits laitiers")).containsExactly("en:milk");
        assertThat(find("matière grasse laitière anhydre")).containsExactly("en:milk");
        assertThat(find("PRESENCE POSSIBLE DE FRUIT A COQUE")).containsExactly("en:nuts");
    }

    @Test
    void englishNut_alone_isANut_butNotInsideOtherWords() {
        // Found by AllergenDetectorTest: "May contain nuts." found nothing.
        assertThat(find("may contain nuts")).containsExactly("en:nuts");
        assertThat(find("traces of tree nuts and peanuts")).containsExactlyInAnyOrder("en:nuts", "en:peanuts");
        assertThat(find("peanut, coconut, nutmeg, butternut squash, nutrition")).containsExactly("en:peanuts");
        assertThat(find("nut-free, gluten free")).isEmpty();
    }

    // ---- The list itself ---------------------------------------------------------------

    @Test
    void theListCantBeChangedFromOutside() {
        assertThatThrownBy(() -> AllergenWordList.ALLERGENS.put("en:pork", Set.of("porc")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}