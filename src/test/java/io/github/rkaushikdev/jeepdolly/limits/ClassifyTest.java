package io.github.rkaushikdev.jeepdolly.limits;

import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ladder: nothing, ordinary, study, superload, and the two things that beat it. */
class ClassifyTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final CommodityBook BOOK = CommodityBook.standard();
    private static final SizeTable SIZE = SizeBook.standard().on(HighwayClass.ARTERIAL);
    private static final StatutoryTable TABLE = StatutoryBook.standard().on(HighwayClass.ARTERIAL, MARCH);

    private static AxleStream legalSemi() {
        return make(List.of(12000, 17000, 17000, 17000, 17000), List.of(200, 54, 324, 54));
    }

    private static Envelope box() {
        return box("8'", "13'");
    }

    private static Envelope box(String width, String height) {
        return Envelope.read(width, height, "70'");
    }

    private static WeightReading read(AxleStream stream) {
        return WeightReading.read(stream, TABLE);
    }

    @Test
    void aLegalLoadNeedsNoPermitAndIsToldSo() {
        Classification verdict = Classification.classify(box(), read(legalSemi()), SIZE, BOOK.get("EXCV"));
        assertThat(verdict.kind()).isSameAs(PermitKind.NONE);
        assertThat(verdict.size()).isSameAs(SizeClass.LEGAL);
        assertThat(verdict.overweight()).isFalse();
        assertThat(verdict.permittable()).isFalse();
        assertThat(verdict).hasToString("no permit needed");
    }

    @Test
    void aWideExcavatorInsideTheRoutineBoxIsAnOrdinaryPermit() {
        Classification verdict = Classification.classify(box("12'", "13'"), read(legalSemi()), SIZE, BOOK.get("EXCV"));
        assertThat(verdict.kind()).isSameAs(PermitKind.ORDINARY);
        assertThat(verdict.kind().issuableAtTheCounter()).isTrue();
        assertThat(verdict.kind().needsEngineer()).isFalse();
        assertThat(verdict).hasToString("ordinary");
    }

    @Test
    void aLegalSizeLoadThatIsOverweightIsStillAnOrdinaryPermit() {
        Classification verdict = Classification.classify(box(), read(tractorLowboy()), SIZE, BOOK.get("EXCV"));
        assertThat(verdict.kind()).isSameAs(PermitKind.ORDINARY);
        assertThat(verdict.overweight()).isTrue();
        assertThat(verdict.size()).isSameAs(SizeClass.LEGAL);
    }

    @Test
    void aLoadPastTheRoutineBoxGoesToAnEngineerAndSaysWhy() {
        Classification verdict = Classification.classify(box("8'", "15'8"), read(legalSemi()), SIZE, BOOK.get("EXCV"));
        assertThat(verdict.kind()).isSameAs(PermitKind.STUDY);
        assertThat(verdict.kind().needsEngineer()).isTrue();
        assertThat(verdict.reviewReasons()).containsExactly("height");
        assertThat(verdict).hasToString("study on height");
    }

    @Test
    void aLoadPastAPublishedThresholdIsASuperload() {
        AxleStream heavy = make(List.of(12000, 30000, 30000, 30000, 30000, 30000), List.of(200, 54, 240, 54, 54));
        Classification verdict = Classification.classify(box(), read(heavy), SIZE, BOOK.get("XFMR"));
        assertThat(verdict.kind()).isSameAs(PermitKind.SUPERLOAD);
        assertThat(verdict.superloadReasons()).contains("gross");
        assertThat(verdict.superloadReasons()).contains("axle group");
    }

    @Test
    void theSuperloadThresholdsBeatTheReviewReasonsInTheReport() {
        Envelope huge = Envelope.read("20'", "18'", "160'");
        Classification verdict = Classification.classify(huge, read(legalSemi()), SIZE, BOOK.get("XFMR"));
        assertThat(verdict.kind()).isSameAs(PermitKind.SUPERLOAD);
        assertThat(verdict.superloadReasons()).containsExactly("width", "height", "length");
        assertThat(verdict.reviewReasons()).isEmpty();
        assertThat(verdict).hasToString("superload on width, height, length");
    }

    @Test
    void aPermitLoadOnAGroupThatIsNotSharingGoesToAnEngineer() {
        AxleStream uneven = make(List.of(12000, 17000, 17000, 14000, 16000, 18000), List.of(200, 54, 180, 52, 52));
        Classification verdict = Classification.classify(box("12'", "13'"), read(uneven), SIZE, BOOK.get("EXCV"));
        assertThat(verdict.kind()).isSameAs(PermitKind.STUDY);
        assertThat(verdict.reviewReasons()).containsExactly("unequal tridem");
        assertThat(verdict).hasToString("study on unequal tridem");
        assertThat(verdict)
                .isEqualTo(Classification.study(SizeClass.ROUTINE, verdict.overweight(), List.of("unequal tridem")));
    }

    @Test
    void theUnequalGroupsFollowTheMeasuresInTheReasons() {
        AxleStream uneven = make(List.of(12000, 15000, 19000, 16000, 16000, 16000), List.of(200, 54, 180, 52, 52));
        Classification verdict = Classification.classify(box("8'", "15'8"), read(uneven), SIZE, BOOK.get("EXCV"));
        assertThat(verdict.reviewReasons()).containsExactly("height", "unequal tandem");
        assertThat(Classification.reasonsForReview(box("8'", "15'8"), read(uneven), SIZE))
                .isEqualTo(verdict.reviewReasons());
    }

    @Test
    void aLegalLoadIsNotSentToAnEngineerForHowItsAxlesShare() {
        AxleStream uneven = make(List.of(12000, 15000, 17000, 13000, 15000), List.of(200, 54, 324, 54));
        Classification verdict = Classification.classify(box(), read(uneven), SIZE, BOOK.get("EXCV"));
        assertThat(read(uneven).unequalGroups()).hasSize(2);
        assertThat(verdict.kind()).isSameAs(PermitKind.NONE);
        assertThat(verdict.reviewReasons()).isEmpty();
    }

    @Test
    void aDivisibleLoadGetsNothingHoweverBigTheNumbersAre() {
        Classification verdict =
                Classification.classify(box("17'", "13'"), read(tractorLowboy()), SIZE, BOOK.get("GRVL"));
        assertThat(verdict.kind()).isSameAs(PermitKind.NONE);
        assertThat(verdict.permittable()).isFalse();
        assertThat(verdict.refusal()).contains("GRVL could have been loaded to legal weight");
        assertThat(verdict).hasToString("no permit: GRVL could have been loaded to legal weight");
        assertThat(verdict)
                .isEqualTo(Classification.refused(
                        SizeClass.REVIEW, verdict.overweight(), "GRVL could have been loaded to legal weight"));
    }

    @Test
    void aDivisibleLoadWithAnAllowanceWalksTheLadderLikeAnythingElse() {
        Classification verdict = Classification.classify(box(), read(tractorLowboy()), SIZE, BOOK.get("WOOD"));
        assertThat(verdict.kind()).isSameAs(PermitKind.ORDINARY);
        assertThat(verdict.permittable()).isTrue();
    }

    @Test
    void aDivisibleLoadThatIsLegalAnywayIsNeverRefused() {
        Classification verdict = Classification.classify(box(), read(legalSemi()), SIZE, BOOK.get("GRVL"));
        assertThat(verdict.kind()).isSameAs(PermitKind.NONE);
        assertThat(verdict.refusal()).isEmpty();
    }

    @Test
    void theThresholdsAreDataAndAStricterSetMovesTheLine() {
        Thresholds strict =
                new Thresholds(new Pounds(90000), new Pounds(40000), Inches.of(10), Inches.of(14), Inches.of(80));
        Classification ordinary = Classification.classify(box("12'", "13'"), read(legalSemi()), SIZE, BOOK.get("EXCV"));
        Classification tightened =
                Classification.classify(box("12'", "13'"), read(legalSemi()), SIZE, BOOK.get("EXCV"), strict);
        assertThat(ordinary.kind()).isSameAs(PermitKind.ORDINARY);
        assertThat(tightened.kind()).isSameAs(PermitKind.SUPERLOAD);
        assertThat(tightened.superloadReasons()).containsExactly("width");
    }

    @Test
    void theAxleGroupThresholdRisesWithTheSizeOfTheGroup() {
        Thresholds standard = Thresholds.STANDARD;
        assertThat(standard.axleGroup(1)).isEqualTo(new Pounds(30000));
        assertThat(standard.axleGroup(2)).isEqualTo(new Pounds(60000));
        assertThat(standard.axleGroup(3)).isEqualTo(new Pounds(70000));
        assertThat(standard.axleGroup(4)).isEqualTo(new Pounds(80000));
        assertThat(standard.axleGroup(6)).isEqualTo(new Pounds(100000));
        assertThatThrownBy(() -> standard.axleGroup(0)).isInstanceOf(Impossible.class);
        assertThat(standard.size("height")).isEqualTo(Inches.of(16));
        assertThat(standard.size("length")).isEqualTo(Inches.of(150));
        assertThatThrownBy(() -> standard.size("rear_overhang"))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("rear_overhang");
    }

    @Test
    void aHeavySingleIsASuperloadWhereTheSameWeightOnAQuadIsNot() {
        AxleStream single = make(List.of(12000, 20000, 20000, 32000), List.of(200, 54, 300));
        Classification heavy = Classification.classify(box(), read(single), SIZE, BOOK.get("XFMR"));
        assertThat(heavy.kind()).isSameAs(PermitKind.SUPERLOAD);
        assertThat(heavy.superloadReasons()).containsExactly("axle group");

        AxleStream quad =
                make(List.of(12000, 20000, 20000, 19000, 19000, 19000, 19000), List.of(200, 54, 300, 52, 52, 52));
        Classification spread = Classification.classify(box(), read(quad), SIZE, BOOK.get("XFMR"));
        assertThat(spread.kind()).isSameAs(PermitKind.ORDINARY);
        assertThat(spread.superloadReasons()).isEmpty();
    }

    @Test
    void aTableThatNamesOneNumberHoldsEveryGroupToIt() {
        Thresholds flat =
                new Thresholds(new Pounds(150000), new Pounds(60000), Inches.of(16), Inches.of(16), Inches.of(150));
        assertThat(flat.axleGroup(1)).isEqualTo(new Pounds(60000));
        assertThat(flat.axleGroup(4)).isEqualTo(new Pounds(60000));
        assertThatThrownBy(() -> new Thresholds(
                        new Pounds(150000),
                        Map.of(2, new Pounds(60000)),
                        Pounds.ZERO,
                        Inches.of(16),
                        Inches.of(16),
                        Inches.of(150)))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("start at the single");
    }

    @Test
    void thePublishedThresholdsAreTheOnesTheOfficeWorksTo() {
        assertThat(Thresholds.STANDARD.gross()).isEqualTo(new Pounds(150000));
        assertThat(Thresholds.STANDARD.width()).isEqualTo(Inches.of(16));
        assertThat(PermitKind.SUPERLOAD).hasToString("superload");
    }
}
