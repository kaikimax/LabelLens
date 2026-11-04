package dev.labellens.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayTest {

    final Display display = new Display();

    @Test
    void numbers_areShortButKeepSmallValuesReadable() {
        assertThat(display.number(30.9)).isEqualTo("30.9");
        assertThat(display.number(3.0)).isEqualTo("3");
        assertThat(display.number(2252.0)).isEqualTo("2252");
        assertThat(display.number(0.107)).isEqualTo("0.11");
        assertThat(display.number(0.0)).isEqualTo("0");
        assertThat(display.number(null)).isEqualTo("not printed");
    }

    @Test
    void allergens_haveReadableNames() {
        assertThat(display.allergen("en:nuts")).isEqualTo("Tree nuts");
        assertThat(display.allergens(List.of("en:milk", "en:soybeans"))).isEqualTo("Milk, Soy");
        assertThat(display.allergens(List.of())).isEqualTo("none found");
        assertThat(display.allergen("en:unknown")).isEqualTo("en:unknown");
    }

    @Test
    void everyEu14Allergen_hasAName() {
        assertThat(Display.ALLERGENS.keySet()).containsExactlyInAnyOrderElementsOf(
                dev.labellens.allergens.AllergenWordList.EU_14);
    }

    @Test
    void gradeClass_andDate() {
        assertThat(display.gradeClass("D")).isEqualTo("grade-d");
        assertThat(display.gradeClass(null)).isEqualTo("grade-none");
        assertThat(display.date(Instant.parse("2026-11-04T18:55:12Z"))).isEqualTo("2026-11-04 18:55 UTC");
    }
}
