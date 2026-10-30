package dev.labellens.allergens;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * Day 6, after the 500-product eval (Claude, at Oussama's request): whitespace normalization
 * (non-breaking spaces), Arabic attached prefixes (و ف ب ك ل), extra Arabic words and
 * plurals, patterns compiled once, map made read-only. Cases: AllergenWordListEvalFindingsTest.
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

    /** Read-only: filled once below, then wrapped so no other class can change the list. */
    public static final Map<String, Set<String>> ALLERGENS;

    /**
     * Arabic writes "and", "so", "with", "like", "for" attached to the next word, before or instead
     * of the article: والتونة (and the tuna), بالسردين (with sardines), للحليب (for milk).
     */
    private static final String ARABIC_PREFIX = "(?:[وف]?[بكل]?ال|[وف]?لل|[وف]?[بكل]|[وف])?";

    /** One compiled pattern per word (compiling for every call was slow in the app). */
    private static final Map<String, List<Pattern>> PATTERNS = new HashMap<>();

    // Phrases that LOOK like an allergen but aren't: blanked out of the text before searching.
    // Smallest misleading piece only, never a whole sentence.
    private static final String[] EXCEPTIONS = {
            // French
            "beurre de cacao", "beurre de karité", "lait de coco", "crème de coco", "lait de noix de coco", "noix de coco",
            "noix de muscade",
            "huile de soja", "matière grasse végétale de soja", "matières grasses végétales de soja", "graisse de soja",
            "sans gluten", "blé noir",
            // Claims after the "may contain" sentence (day 6): "Sans blé. Sans oeufs. Sans fruits à coque."
            // "sans lactose" is a claim too: lactose-free milk still says "lait", so it stays milk.
            "sans blé", "sans oeufs", "sans œufs", "sans fruits à coque", "sans lactose",
            // Flavours named after a nut, with no nut in them (day 6: chicory, chocolate milk)
            "arôme noisette", "arôme de noisette", "goût noisette", "arôme amande", "arôme d'amande",
            // English
            "cocoa butter", "shea butter", "coconut milk", "coconut cream",
            "soybean oil", "soya oil", "soy oil", "gluten-free", "gluten free",
            // Arabic
            "زبدة الكاكاو", "حليب جوز الهند", "جوز الهند", "زيت الصويا", "خالي من الغلوتين", "بدون غلوتين"
    };

    private static final Pattern ANY_SPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    static {
        Map<String, Set<String>> words = new HashMap<>();
        words.put("en:gluten", Set.of("blé", "orge", "seigle", "avoine", "épeautre", "gluten", "malt",
                "wheat", "barley", "rye", "oat", "oats", "spelt",
                "قمح", "شعير", "شوفان", "غلوتين", "جلوتين"));
        words.put("en:milk", Set.of("lait", "lactose", "lactosérum", "beurre", "crème", "fromage", "caséine", "caséinate",
                "protéines de lait", "babeurre", "produits laitiers", "dérivés laitiers", "matière grasse laitière",
                "milk", "butter", "cream", "cheese", "whey", "casein", "caseinate", "buttermilk", "yogurt", "yoghurt",
                "حليب", "لبن", "ألبان", "البان", "جبن", "أجبان", "اجبان", "جبنة", "زبدة", "زبده", "قشدة", "كريمة",
                "مصل اللبن", "لاكتوز"));
        words.put("en:eggs", Set.of("œuf", "œufs", "jaune d'œuf", "oeufs", "oeuf",
                "egg", "egg yolk",
                "بيض", "بيضة", "صفار البيض"));
        words.put("en:soybeans", Set.of("soja", "lécithine de soja",
                "soy", "soya", "soybean",
                "صويا"));
        words.put("en:peanuts", Set.of("arachide", "cacahuète",
                "peanut", "groundnut",
                "فول سوداني", "فول السوداني", "فستق سوداني", "فستق السوداني"));
        words.put("en:nuts", Set.of("amande", "noisette", "noix", "pistache", "cajou", "fruits à coque", "fruit à coque", "fruits a coque", "fruit a coque",
                "almond", "hazelnut", "walnut", "cashew", "pistachio", "pistachios", "pecan", "macadamia",
                "لوز", "بندق", "جوز", "كاجو", "فستق حلبي", "مكسرات"));
        words.put("en:sesame-seeds", Set.of("sésame",
                "sesame",
                "سمسم", "طحينة", "طحينية"));
        words.put("en:mustard", Set.of("moutarde",
                "mustard",
                "خردل", "مستردة"));
        words.put("en:sulphur-dioxide-and-sulphites", Set.of("sulfites", "anhydride sulfureux", "métabisulfite",
                "sulfite", "sulphite", "sulphur dioxide", "sulfur dioxide", "metabisulfite", "metabisulphite",
                "e220", "e221", "e222", "e223", "e224", "e225", "e226", "e227", "e228",
                "كبريتيت", "كبريتات", "ثاني أكسيد الكبريت"));
        words.put("en:fish", Set.of("poisson", "thon", "saumon", "maquereau", "anchois", "cabillaud", "colin", "merlu",
                "hareng", "morue", "truite", "sardine",
                "fish", "tuna", "salmon", "cod", "anchovy", "anchovies", "herring", "hake", "trout", "mackerel",
                "تونة", "تونا", "سردين", "سردينة", "سمك", "سمكة", "أسماك", "اسماك", "ماكريل", "إسقمري", "اسقمري",
                "رنجة", "نازلي", "سلمون", "أنشوفة", "أنشوجة"));
        words.put("en:crustaceans", Set.of("crevette", "crustacés",
                "shrimp", "prawn", "crab", "lobster", "crustacean",
                "قشريات", "روبيان", "جمبري", "قريدس"));
        words.put("en:molluscs", Set.of("mollusques", "moules",
                "mussel", "oyster", "squid", "clam", "mollusc", "mollusk",
                "رخويات", "محار", "حبار"));
        words.put("en:celery", Set.of("céleri",
                "celery", "celeriac",
                "كرفس"));
        words.put("en:lupin", Set.of("lupin",
                "lupine",
                "ترمس"));

        words.forEach((tag, list) -> {
            List<Pattern> patterns = new ArrayList<>();
            for (String word : list) {
                // Arabic words: optional attached prefixes. Latin words: optional plural s or x.
                boolean arabic = word.codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC);
                String before = arabic ? ARABIC_PREFIX : "";
                String after = arabic ? "" : "(s|x)?";
                patterns.add(Pattern.compile("\\b" + before + Pattern.quote(normalize(word)) + after + "\\b",
                        Pattern.UNICODE_CHARACTER_CLASS));
            }
            PATTERNS.put(tag, List.copyOf(patterns));
        });
        ALLERGENS = Collections.unmodifiableMap(words);
    }

    /** Same steps for the text and the words: NFC, lowercase, every kind of space → one plain space. */
    private static String normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        // \s with UNICODE_CHARACTER_CLASS includes the non-breaking spaces (U+00A0, U+202F) that
        // Open Food Facts texts contain: "fruits à coque" with one was missed in the day 6 eval.
        return ANY_SPACE.matcher(t).replaceAll(" ");
    }

    public static Set<String> find(String ingredients) {
        Set<String> output = new HashSet<>();
        if (ingredients == null) {
            return output;
        }

        String prepared = normalize(ingredients);
        for (String exception : EXCEPTIONS) {
            prepared = prepared.replace(exception, " ");
        }
        prepared = prepared.replace("beurre de cacahuète", "cacahuète");
        prepared = prepared.replace("peanut butter", "peanut");
        // Plant "milks" are not milk, but keep the plant: "lait d'amande" is still almond (day 6).
        prepared = prepared.replace("lait d'amande", "amande");
        prepared = prepared.replace("lait de soja", "soja");
        prepared = prepared.replace("lait de riz", "riz");
        prepared = prepared.replace("lait d'avoine", "avoine");

        for (Map.Entry<String, List<Pattern>> entry : PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(prepared).find()) {
                    output.add(entry.getKey());
                    break;
                }
            }
        }
        return output;
    }
}
