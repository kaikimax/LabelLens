package dev.labellens.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Small display helpers for the templates, used as ${@display.allergen(tag)}.
 * Kept in Java so they can be unit-tested, instead of logic spread over the HTML.
 */
@Component("display")
public class Display {

    static final Map<String, String> ALLERGENS = Map.ofEntries(
            Map.entry("en:milk", "Milk"),
            Map.entry("en:gluten", "Gluten"),
            Map.entry("en:soybeans", "Soy"),
            Map.entry("en:eggs", "Eggs"),
            Map.entry("en:nuts", "Tree nuts"),
            Map.entry("en:peanuts", "Peanuts"),
            Map.entry("en:mustard", "Mustard"),
            Map.entry("en:fish", "Fish"),
            Map.entry("en:celery", "Celery"),
            Map.entry("en:sesame-seeds", "Sesame"),
            Map.entry("en:sulphur-dioxide-and-sulphites", "Sulphites"),
            Map.entry("en:crustaceans", "Crustaceans"),
            Map.entry("en:molluscs", "Molluscs"),
            Map.entry("en:lupin", "Lupin"));

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public String allergen(String tag) {
        return ALLERGENS.getOrDefault(tag, tag);
    }

    /** "Milk, Tree nuts" — or "none found" for an empty list. */
    public String allergens(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "none found";
        }
        return String.join(", ", tags.stream().map(this::allergen).toList());
    }

    /** Server time in UTC: the app doesn't know the visitor's time zone. */
    public String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant);
    }

    /** "0.107" → "0.11", "30.9" → "30.9", "3.0" → "3", null → "not printed". */
    public String number(Double value) {
        if (value == null) {
            return "not printed";
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(value < 1 ? 2 : 1, RoundingMode.HALF_UP);
        return rounded.stripTrailingZeros().toPlainString();
    }

    /** CSS class for the grade colour: "grade-a" … "grade-e". */
    public String gradeClass(String grade) {
        return grade == null ? "grade-none" : "grade-" + grade.toLowerCase();
    }
}
