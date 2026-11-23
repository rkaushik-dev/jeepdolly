package io.github.rkaushikdev.jeepdolly.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.support.Structures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rating a load over a structure, and the three bands the answer falls in. */
class RatingTest {

    private static final StructureBook BOOK = Structures.structureBook();
    private static final Structure KENNEBEC = BOOK.get("1247");
    private static final Structure CARRABASSETT = BOOK.get("1533");
    private static final Structure CULVERT = BOOK.get("3310");
    private static final Structure OVERPASS = BOOK.get("2201");

    @Test
    void theFactorIsCapacityOverTheLoadInWholePercent() {
        assertThat(Rating.factorFor(KENNEBEC, new Pounds(94000))).isEqualTo(140);
        assertThat(Rating.factorFor(KENNEBEC, new Pounds(132000))).isEqualTo(100);
        assertThat(Rating.factorFor(CULVERT, new Pounds(82000))).isEqualTo(109);
    }

    @Test
    void aStructureIsDueWithinTwentyFourMonthsOfItsLastInspection() {
        assertThat(KENNEBEC.inspected()).contains(Structures.INSPECTED);
        assertThat(KENNEBEC.inspectionOverdue(LocalDate.of(2026, 6, 1))).isFalse();
        assertThat(KENNEBEC.inspectionOverdue(LocalDate.of(2026, 6, 2))).isTrue();
        Structure unknown = Structure.builder("9001", "Wesserunsett Stream", "SR43-1", StructureKind.of("bridge"))
                .spansFeet(List.of(40))
                .operatingRating(new Pounds(100000))
                .build();
        assertThat(unknown.inspectionOverdue(LocalDate.of(2024, 1, 1))).isTrue();
    }

    @Test
    void aClearRatingOnAnOverdueStructureCrossesUnderConditions() {
        Rating rating = Rating.rate(KENNEBEC, new Pounds(94000), LocalDate.of(2026, 9, 1));
        assertThat(rating.verdict()).isSameAs(Verdict.CONDITIONAL);
        assertThat(rating.factor()).isEqualTo(140);
        assertThat(rating.held()).contains(Rating.INSPECTION_OVERDUE);
        assertThat(rating).hasToString("1247 rates 140% for 94,000 lb, conditional (inspection overdue)");
        assertThat(rating.crossingConditions()).hasSize(3);
    }

    @Test
    void anOverdueInspectionNeverMakesARatingKinderOrHarsherThanConditional() {
        LocalDate late = LocalDate.of(2026, 9, 1);
        assertThat(Rating.rate(KENNEBEC, new Pounds(125000), late))
                .isEqualTo(Rating.rate(KENNEBEC, new Pounds(125000)));
        assertThat(Rating.rate(KENNEBEC, new Pounds(140000), late).verdict()).isSameAs(Verdict.REFUSED);
        assertThat(Rating.rate(CARRABASSETT, new Pounds(94000), late).postedOut())
                .isTrue();
        assertThat(Rating.rate(KENNEBEC, new Pounds(94000), LocalDate.of(2025, 4, 8)))
                .isEqualTo(Rating.rate(KENNEBEC, new Pounds(94000)));
    }

    @Test
    void aComfortableFactorCrossesWithNothingAttached() {
        Rating rating = Rating.rate(KENNEBEC, new Pounds(94000));
        assertThat(rating.verdict()).isSameAs(Verdict.CLEARS);
        assertThat(rating.verdict().crosses()).isTrue();
        assertThat(rating.crossingConditions()).isEmpty();
        assertThat(rating).hasToString("1247 rates 140% for 94,000 lb, clears");
    }

    @Test
    void aFactorInTheMiddleBandCrossesUnderThreeConditions() {
        Rating rating = Rating.rate(KENNEBEC, new Pounds(125000));
        assertThat(rating.factor()).isEqualTo(105);
        assertThat(rating.verdict()).isSameAs(Verdict.CONDITIONAL);
        List<String> conditions = rating.crossingConditions();
        assertThat(conditions).hasSize(3);
        assertThat(conditions.get(0)).startsWith("cross Kennebec River alone");
        assertThat(conditions.get(1)).contains("walking pace");
        assertThat(conditions.get(2)).contains("centred");
    }

    @Test
    void aFactorUnderOneHundredDoesNotCrossAtAll() {
        Rating rating = Rating.rate(KENNEBEC, new Pounds(140000));
        assertThat(rating.factor()).isEqualTo(94);
        assertThat(rating.verdict()).isSameAs(Verdict.REFUSED);
        assertThat(rating.verdict().crosses()).isFalse();
        assertThat(rating.crossingConditions()).isEmpty();
    }

    @Test
    void theBandEdgesAreWhereTheOfficeSaysTheyAre() {
        assertThat(Rating.rate(CULVERT, new Pounds(82000)).verdict()).isSameAs(Verdict.CONDITIONAL);
        assertThat(Rating.rate(CULVERT, new Pounds(81000)).verdict()).isSameAs(Verdict.CLEARS);
        assertThat(Rating.rate(CULVERT, new Pounds(90000)).verdict()).isSameAs(Verdict.CONDITIONAL);
        assertThat(Rating.rate(CULVERT, new Pounds(90001)).verdict()).isSameAs(Verdict.REFUSED);
    }

    @Test
    void aPostingBeatsTheArithmeticAndSaysSo() {
        Rating rating = Rating.rate(CARRABASSETT, new Pounds(94000));
        assertThat(rating.postedOut()).isTrue();
        assertThat(rating.verdict()).isSameAs(Verdict.REFUSED);
        assertThat(rating.factor()).isEqualTo(0);
        assertThat(rating).hasToString("1533 posted at 48,000 lb, load refused");
    }

    @Test
    void aLoadInsideThePostingIsRatedLikeAnythingElse() {
        Rating rating = Rating.rate(CARRABASSETT, new Pounds(40000));
        assertThat(rating.postedOut()).isFalse();
        assertThat(rating.factor()).isEqualTo(150);
        assertThat(rating.verdict()).isSameAs(Verdict.CLEARS);
        assertThat(rating.capacity()).isEqualTo(new Pounds(60000));
    }

    @Test
    void aStructureWithNoOperatingRatingCannotBeRated() {
        assertThatThrownBy(() -> Rating.factorFor(OVERPASS, new Pounds(94000)))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("no operating rating");
    }

    @Test
    void aLoadWithNoWeightIsNotARatingQuestion() {
        assertThatThrownBy(() -> Rating.factorFor(KENNEBEC, new Pounds(0))).isInstanceOf(Impossible.class);
    }

    @Test
    void theSignAStructureWouldCarryComesOffTheInventoryRating() {
        assertThat(Rating.postingFor(KENNEBEC)).isEqualTo(new Pounds(88000));
        assertThat(Rating.postingFor(CARRABASSETT)).isEqualTo(new Pounds(42000));
    }

    @Test
    void aSignThatDisagreesWithTheLastInspectionIsReported() {
        assertThat(Rating.postingIsCurrent(CARRABASSETT)).isFalse();
        assertThat(Rating.postingIsCurrent(KENNEBEC)).isTrue();
    }

    @Test
    void aStructureWithNoInventoryRatingCannotHaveASignDerived() {
        assertThatThrownBy(() -> Rating.postingFor(OVERPASS)).isInstanceOf(Impossible.class);
    }
}
