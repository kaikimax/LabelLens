package dev.labellens.allergens;

// import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the word list must do. Written before the code (same method as NutriScoreCalculatorTest).
 *
 * Each case is a rule from docs/DATA_PLAN.md or a trap a naive list falls into.
 * If you disagree with a case, change it AND write the reason in the decisions log.
 */
// @Disabled("Day 6: remove this line when you start writing AllergenWordList")
class AllergenWordListTest {

    static Set<String> find(String text) {
        return AllergenWordList.find(text);
    }

    // ---- Basics ------------------------------------------------------------------------

    @Test
    void aTypicalBiscuit() {
        assertThat(find("Farine de blé, sucre, huile de palme, lait écrémé en poudre, lécithine de soja, sel."))
                .containsExactlyInAnyOrder("en:gluten", "en:milk", "en:soybeans");
    }

    @Test
    void noAllergen() {
        assertThat(find("Fraises, sucre, gélifiant : pectine de fruits, acide citrique.")).isEmpty();
    }

    @Test
    void nullOrEmpty_findsNothing() {
        assertThat(find(null)).isEmpty();
        assertThat(find("")).isEmpty();
        assertThat(find("   ")).isEmpty();
    }

    @Test
    void caseDoesNotMatter() {
        // Labels often print allergens in capitals (p002: "ARACHIDES").
        assertThat(find("ARACHIDES grillées (90%), sucre")).containsExactly("en:peanuts");
        assertThat(find("FARINE DE BLÉ")).containsExactly("en:gluten");
    }

    @Test
    void onlyTheFourteenTagsAreUsed() {
        assertThat(find("Farine de blé, lait, œufs, soja, noisettes, moutarde, sésame, céleri"))
                .isSubsetOf(AllergenWordList.EU_14);
    }

    // ---- Every allergen, French ----------------------------------------------------------

    @Test
    void eachAllergen_hasAtLeastOneFrenchWord() {
        assertThat(find("orge")).containsExactly("en:gluten");
        assertThat(find("lactose")).containsExactly("en:milk");
        assertThat(find("jaune d'œuf")).containsExactly("en:eggs");
        assertThat(find("soja")).containsExactly("en:soybeans");
        assertThat(find("cacahuètes")).containsExactly("en:peanuts");
        assertThat(find("noisettes")).containsExactly("en:nuts");
        assertThat(find("graines de sésame")).containsExactly("en:sesame-seeds");
        assertThat(find("moutarde")).containsExactly("en:mustard");
        assertThat(find("anhydride sulfureux")).containsExactly("en:sulphur-dioxide-and-sulphites");
        assertThat(find("thon")).containsExactly("en:fish");
        assertThat(find("crevettes")).containsExactly("en:crustaceans");
        assertThat(find("moules")).containsExactly("en:molluscs");
        assertThat(find("céleri")).containsExactly("en:celery");
        assertThat(find("farine de lupin")).containsExactly("en:lupin");
    }

    // ---- Derived names: the allergen hides behind another word ---------------------------

    @Test
    void milkDerivatives_areMilk() {
        assertThat(find("lactosérum en poudre")).containsExactly("en:milk");
        assertThat(find("caséinate de sodium")).containsExactly("en:milk");
        assertThat(find("babeurre")).containsExactly("en:milk");
    }

    @Test
    void wheatInAnyForm_isGluten() {
        assertThat(find("semoule de blé dur")).containsExactly("en:gluten");
        assertThat(find("amidon de blé")).containsExactly("en:gluten");
        assertThat(find("épeautre")).containsExactly("en:gluten");
    }

    @Test
    void sulphites_hideInAdditives() {
        assertThat(find("conservateur : métabisulfite de potassium")).containsExactly("en:sulphur-dioxide-and-sulphites");
        assertThat(find("conservateur : E224")).containsExactly("en:sulphur-dioxide-and-sulphites");
        assertThat(find("antioxydant (E220)")).containsExactly("en:sulphur-dioxide-and-sulphites");
    }

    @Test
    void eggWithoutTheLigature_isStillEgg() {
        // Keyboards often type "oeuf" instead of "œuf".
        assertThat(find("oeufs entiers")).containsExactly("en:eggs");
    }

    @Test
    void lactoseFreeMilk_isStillMilk() {
        assertThat(find("lait entier sans lactose, vitamines A et D3")).containsExactly("en:milk");
    }

    // ---- Traps: the word is there, the allergen is not -----------------------------------

    @Test
    void lettuce_isNotMilk() {
        // "lait" is inside "laitue".
        assertThat(find("laitue, tomates, huile d'olive")).isEmpty();
    }

    @Test
    void cocoaButter_isNotMilk() {
        assertThat(find("sucre, beurre de cacao, pâte de cacao")).isEmpty();
    }

    @Test
    void peanutButter_isPeanutNotMilk() {
        assertThat(find("beurre de cacahuète")).containsExactly("en:peanuts");
    }

    @Test
    void coconut_isNeitherMilkNorNut() {
        // Coconut isn't on the EU nut list, and coconut milk isn't milk.
        assertThat(find("lait de coco, noix de coco râpée")).isEmpty();
    }

    @Test
    void soybeanOil_doesNotCount() {
        // DATA_PLAN: fully refined oils are exempt under EU rules.
        assertThat(find("huile de soja, sel")).isEmpty();
        assertThat(find("huile de soja, lécithine de soja")).containsExactly("en:soybeans");
    }

    @Test
    void glutenFree_isNotGluten() {
        assertThat(find("farine de riz, amidon de maïs, sans gluten")).isEmpty();
    }

    @Test
    void buckwheat_isNotWheat() {
        // "blé noir" (buckwheat) contains no gluten, despite the word "blé".
        assertThat(find("farine de blé noir, sel")).isEmpty();
    }

    // ---- Other languages in the test data ------------------------------------------------

    @Test
    void english() {
        // 2,405 Moroccan products in Open Food Facts have an English ingredients text.
        assertThat(find("Wheat flour, sugar, skimmed milk powder, soy lecithin, eggs"))
                .containsExactlyInAnyOrder("en:gluten", "en:milk", "en:soybeans", "en:eggs");
    }

    @Test
    void arabic() {
        assertThat(find("دقيق القمح، سكر، حليب")).containsExactlyInAnyOrder("en:gluten", "en:milk");
        assertThat(find("ماء، زيت، صفار البيض، خردل")).containsExactlyInAnyOrder("en:eggs", "en:mustard");
    }

        // ---- Traps in other forms: the exceptions must be pieces, not whole test sentences ----

    @Test
    void coconut_inAnyPosition_isNeitherMilkNorNut() {
        assertThat(find("lait de coco, sucre")).isEmpty();
        assertThat(find("sucre, noix de coco râpée")).isEmpty();
        assertThat(find("crème de coco")).isEmpty();
        assertThat(find("coconut milk, water")).isEmpty();
        assertThat(find("coconut cream")).isEmpty();
    }

    @Test
    void soybeanOil_inAnyPosition_doesNotCount() {
        assertThat(find("huile de soja, sucre")).isEmpty();
        assertThat(find("huile de soja raffinée, sel")).isEmpty();
        assertThat(find("soybean oil, salt")).isEmpty();
        assertThat(find("زيت الصويا، ملح")).isEmpty();
    }

    @Test
    void cocoaAndSheaButter_inEnglishToo_areNotMilk() {
        assertThat(find("cocoa butter, sugar")).isEmpty();
        assertThat(find("sucre, beurre de karité")).isEmpty();
    }

    @Test
    void peanutButter_inEnglish_isPeanutNotMilk() {
        assertThat(find("peanut butter")).containsExactly("en:peanuts");
    }

    @Test
    void buckwheat_inOtherDishes_isNotWheat() {
        assertThat(find("galette de blé noir")).isEmpty();
        assertThat(find("buckwheat flour, water")).isEmpty();
    }

    @Test
    void glutenFree_inEnglish_isNotGluten() {
        assertThat(find("gluten-free rice flour")).isEmpty();
    }

    @Test
    void anAccentStoredAsTwoCharacters_isStillFound() {
        assertThat(find("farine de ble\u0301, sucre")).containsExactly("en:gluten");
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

    // ---- The list itself ---------------------------------------------------------------

    @Test
    void theListCantBeChangedFromOutside() {
        assertThatThrownBy(() -> AllergenWordList.ALLERGENS.put("en:pork", Set.of("porc")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
