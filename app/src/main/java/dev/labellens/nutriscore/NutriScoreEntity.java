package dev.labellens.nutriscore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of the nutri_score table (V4__create_nutri_score.sql): one per product. */
@Entity
@Table(name = "nutri_score")
public class NutriScoreEntity {

    public static final String ALGORITHM_VERSION = "2023-general";

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private boolean scored;

    @Column(name = "not_scored_reason", length = 300)
    private String notScoredReason;

    @JdbcTypeCode(SqlTypes.CHAR) // the column is CHAR(1); without this Hibernate expects VARCHAR
    @Column(length = 1)
    private String grade;

    private Integer score;

    @Column(name = "energy_points")
    private Integer energyPoints;
    @Column(name = "sugars_points")
    private Integer sugarsPoints;
    @Column(name = "saturated_fat_points")
    private Integer saturatedFatPoints;
    @Column(name = "salt_points")
    private Integer saltPoints;
    @Column(name = "protein_points")
    private Integer proteinPoints;
    @Column(name = "fibre_points")
    private Integer fibrePoints;
    @Column(name = "fruit_veg_legumes_points")
    private Integer fruitVegLegumesPoints;
    @Column(name = "protein_counted")
    private Boolean proteinCounted;

    @Column(name = "fruit_veg_assumed_zero", nullable = false)
    private boolean fruitVegAssumedZero;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NutriScoreEntity() {
    }

    public static NutriScoreEntity from(UUID productId, NutriScoreResult result, boolean fruitVegAssumedZero) {
        NutriScoreEntity e = new NutriScoreEntity();
        e.productId = productId;
        e.scored = result.scored();
        e.notScoredReason = result.notScoredReason();
        e.grade = result.grade();
        e.score = result.score();
        NutriScorePoints p = result.points();
        if (p != null) {
            e.energyPoints = p.energy();
            e.sugarsPoints = p.sugars();
            e.saturatedFatPoints = p.saturatedFat();
            e.saltPoints = p.salt();
            e.proteinPoints = p.protein();
            e.fibrePoints = p.fibre();
            e.fruitVegLegumesPoints = p.fruitVegLegumes();
            e.proteinCounted = p.proteinCounted();
        }
        e.fruitVegAssumedZero = fruitVegAssumedZero;
        e.algorithmVersion = ALGORITHM_VERSION;
        e.createdAt = Instant.now();
        return e;
    }

    public NutriScoreResult toResult() {
        NutriScorePoints points = energyPoints == null ? null : new NutriScorePoints(
                energyPoints, sugarsPoints, saturatedFatPoints, saltPoints,
                proteinPoints, fibrePoints, fruitVegLegumesPoints, proteinCounted);
        return new NutriScoreResult(scored, notScoredReason, grade, score, points);
    }

    public boolean isFruitVegAssumedZero() {
        return fruitVegAssumedZero;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }
}
