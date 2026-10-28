package dev.labellens.allergens;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the 14 EU allergens in an ingredients text by looking for known words.
 * The predictable half of allergen detection (decisions log, 2026-10-26): it never varies
 * between runs, and its answer is combined with the AI's.
 *
 * ---------------------------------------------------------------------------------
 * Day 6: written by Oussama. AllergenWordListTest says precisely what it must do.
 *
 *   - Input: the ingredients text ONLY (the "may contain" sentence is removed before,
 *     by another class). It can be null, French, English or Arabic, in any case.
 *   - Output: the set of tags found, using exactly the 14 tags below. Empty set if none.
 *   - The word table is in docs/DATA_PLAN.md ("Finding allergens in the ingredients"),
 *     including its rules (soybean oil doesn't count, sans lactose is still milk...).
 *     The tests add a few words and traps the table doesn't have.
 *
 * The traps are the point: a word list that only looks for "lait" also finds it in
 * "laitue" (lettuce). Think about how a word is recognised before writing the list.
 * ---------------------------------------------------------------------------------
 */
public final class AllergenWordList {

    public static final Set<String> EU_14 = Set.of(
            "en:milk", "en:gluten", "en:soybeans", "en:eggs", "en:nuts", "en:mustard", "en:fish",
            "en:peanuts", "en:celery", "en:sesame-seeds", "en:sulphur-dioxide-and-sulphites",
            "en:crustaceans", "en:molluscs", "en:lupin");

    private AllergenWordList() {
    }

    public static final Map<String, Set<String>> ALLERGENS = new HashMap<>();

    static {
        ALLERGENS.put("en:gluten", Set.of("blé", "orge", "seigle", "avoine", "épeautre", "gluten", "malt",
                "wheat", "barley", "rye", "oat", "oats", "spelt",
                "قمح", "شعير"));
        ALLERGENS.put("en:milk", Set.of("lait", "lactose", "lactosérum", "beurre", "crème", "fromage", "caséine", "caséinate",
                "protéines de lait", "babeurre",
                "milk", "butter", "cream", "cheese", "whey", "casein", "caseinate", "buttermilk", "yogurt", "yoghurt",
                "حليب"));
        ALLERGENS.put("en:eggs", Set.of("œuf", "œufs", "jaune d'œuf", "oeufs", "oeuf",
                "egg", "egg yolk",
                "بيض"));
        ALLERGENS.put("en:soybeans", Set.of("soja", "lécithine de soja",
                "soy", "soya", "soybean",
                "صويا"));
        ALLERGENS.put("en:peanuts", Set.of("arachide", "cacahuète",
                "peanut", "groundnut",
                "فول سوداني", "الفول السوداني"));
        ALLERGENS.put("en:nuts", Set.of("amande", "noisette", "noix", "pistache", "cajou",
                "almond", "hazelnut", "walnut", "cashew", "pistachio", "pistachios", "pecan", "macadamia",
                "لوز", "بندق"));
        ALLERGENS.put("en:sesame-seeds", Set.of("sésame",
                "sesame",
                "سمسم"));
        ALLERGENS.put("en:mustard", Set.of("moutarde",
                "mustard",
                "خردل"));
        ALLERGENS.put("en:sulphur-dioxide-and-sulphites", Set.of("sulfites", "anhydride sulfureux", "métabisulfite",
                "sulfite", "sulphite", "sulphur dioxide", "sulfur dioxide", "metabisulfite", "metabisulphite",
                "e220", "e221", "e222", "e223", "e224", "e225", "e226", "e227", "e228"));
        ALLERGENS.put("en:fish", Set.of("poisson", "thon", "سمك", "saumon",
                "fish", "tuna", "salmon", "cod", "anchovy", "anchovies", "sardine"));
        ALLERGENS.put("en:crustaceans", Set.of("crevette", "crustacés",
                "shrimp", "prawn", "crab", "lobster", "crustacean"));
        ALLERGENS.put("en:molluscs", Set.of("mollusques", "moules",
                "mussel", "oyster", "squid", "clam", "mollusc", "mollusk"));
        ALLERGENS.put("en:celery", Set.of("céleri",
                "celery", "celeriac"));
        ALLERGENS.put("en:lupin", Set.of("lupin",
                "lupine"));
    }
    

    public static Set<String> find(String ingredients) {
        Set<String> output = new HashSet<>();
        if (ingredients == null) return output; // handle null case

        
        ingredients = Normalizer.normalize(ingredients, Normalizer.Form.NFC);
        ingredients = ingredients.toLowerCase(Locale.ROOT); // Handle Case mismatch

        // Phrases that LOOK like an allergen but aren't: blanked out of the text before searching.
        // Smallest misleading piece only, never a whole sentence.
        String[] exceptions = {
                // French
                "beurre de cacao", "beurre de karité", "lait de coco", "crème de coco", "lait de noix de coco", "noix de coco",
                "huile de soja", "sans gluten", "blé noir",
                // English
                "cocoa butter", "shea butter", "coconut milk", "coconut cream",
                "soybean oil", "soya oil", "soy oil", "gluten-free", "gluten free",
                // Arabic
                "زبدة الكاكاو", "حليب جوز الهند", "زيت الصويا", "خالي من الغلوتين", "بدون غلوتين"
        };
        for (String exception:exceptions) {
            ingredients = ingredients.replace(exception, " ");
        }
        
        ingredients = ingredients.replace("beurre de cacahuète", "cacahuète");
        ingredients = ingredients.replace("peanut butter", "peanut");
        String prepared = ingredients;

        ALLERGENS.forEach((allergenName, listOfAllergens) -> {
            for (String allergen: listOfAllergens) {
                boolean contains = Pattern.compile("\\b(ال)?" + Pattern.quote(allergen) + "s?\\b", Pattern.UNICODE_CHARACTER_CLASS)
                                            .matcher(prepared)
                                            .find();
                if (contains) {
                    output.add(allergenName);
                }
            }
        });
        
        return output;
    }
}
