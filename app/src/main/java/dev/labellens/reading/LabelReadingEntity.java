package dev.labellens.reading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Row of the label_reading table (V2__create_label_reading.sql): one per product.
 * Its primary key IS the product id, since a product has at most one reading.
 */
@Entity
@Table(name = "label_reading")
public class LabelReadingEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(length = 300)
    private String name;
    @Column(length = 300)
    private String brand;
    @Column(length = 20)
    private String type;

    @Column(name = "energy_kj")
    private Double energyKj;
    private Double fat;
    @Column(name = "saturated_fat")
    private Double saturatedFat;
    private Double carbohydrates;
    private Double sugars;
    private Double fibre;
    private Double protein;
    private Double salt;

    @Column(name = "ingredients_text", columnDefinition = "text")
    private String ingredientsText;
    @Column(name = "ingredients_language", length = 10)
    private String ingredientsLanguage;
    @Column(name = "printed_basis", length = 10)
    private String printedBasis;
    @Column(name = "may_contain_text", columnDefinition = "text")
    private String mayContainText;

    /** Warnings joined with newlines (a warning never contains a newline). */
    @Column(nullable = false, columnDefinition = "text")
    private String warnings;

    @Column(length = 100)
    private String model;

    /** The model's exact answer: invaluable when a reading is wrong and you need to see why. */
    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LabelReadingEntity() {
    }

    public static LabelReadingEntity from(UUID productId, ReadOutcome outcome) {
        LabelReading r = outcome.reading();
        LabelReadingEntity e = new LabelReadingEntity();
        e.productId = productId;
        e.name = r.name();
        e.brand = r.brand();
        e.type = r.type();
        e.energyKj = r.energyKj();
        e.fat = r.fat();
        e.saturatedFat = r.saturatedFat();
        e.carbohydrates = r.carbohydrates();
        e.sugars = r.sugars();
        e.fibre = r.fibre();
        e.protein = r.protein();
        e.salt = r.salt();
        e.ingredientsText = r.ingredientsText();
        e.ingredientsLanguage = r.ingredientsLanguage();
        e.printedBasis = r.printedBasis();
        e.mayContainText = r.mayContainText();
        e.warnings = String.join("\n", outcome.warnings());
        e.model = outcome.model();
        e.rawResponse = outcome.rawResponse();
        e.createdAt = Instant.now();
        return e;
    }

    public LabelReading toReading() {
        return new LabelReading(name, brand, type, energyKj, fat, saturatedFat, carbohydrates,
                sugars, fibre, protein, salt, ingredientsText, ingredientsLanguage, printedBasis,
                mayContainText);
    }

    public List<String> getWarnings() {
        return warnings.isEmpty() ? List.of() : Arrays.asList(warnings.split("\n"));
    }

    public String getModel() {
        return model;
    }
}
