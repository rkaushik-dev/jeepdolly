package io.github.rkaushikdev.jeepdolly.limits;

import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Span;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The bridge formula, checked against the published table and the exception. */
class FormulaTest {

    private static final Pounds ARTERIAL = new Pounds(100000);
    private static final Pounds INTERSTATE = new Pounds(80000);

    /** Five axle semi at eighty thousand, tandems {@code gap} inches apart. */
    private static AxleStream semi(int gap) {
        return make(List.of(12000, 17000, 17000, 17000, 17000), List.of(200, 54, gap, 54));
    }

    private static AxleStream semi() {
        return semi(324);
    }

    private static Span run(AxleStream stream, int first, int last) {
        return stream.spans().stream()
                .filter(one -> one.first() == first && one.last() == last)
                .findFirst()
                .orElseThrow();
    }

    private static List<Integer> ends(Span span) {
        return List.of(span.first(), span.last());
    }

    @Test
    void aDistanceRoundsToTheNearestWholeFoot() {
        assertThat(Formula.feetOf(new Inches(48))).isEqualTo(4);
        assertThat(Formula.feetOf(new Inches(53))).isEqualTo(4);
        assertThat(Formula.feetOf(new Inches(54))).isEqualTo(5);
        assertThat(Formula.feetOf(new Inches(0))).isEqualTo(0);
    }

    @Test
    void aNegativeDistanceIsNotARunOfAxles() {
        assertThatThrownBy(() -> Formula.feetOf(new Inches(-12))).isInstanceOf(Impossible.class);
    }

    @Test
    void anAnswerRoundsToTheNearestFiveHundredPounds() {
        assertThat(Formula.roundTo500(79875)).isEqualTo(new Pounds(80000));
        assertThat(Formula.roundTo500(79700)).isEqualTo(new Pounds(79500));
        assertThat(Formula.roundTo500(34000)).isEqualTo(new Pounds(34000));
    }

    @Test
    void theFormulaAgreesWithTheWallChart() {
        assertThat(Formula.limit(2, Inches.of(4), ARTERIAL)).isEqualTo(new Pounds(34000));
        assertThat(Formula.limit(3, Inches.of(10), ARTERIAL)).isEqualTo(new Pounds(43500));
        assertThat(Formula.limit(4, Inches.of(30), ARTERIAL)).isEqualTo(new Pounds(62000));
        assertThat(Formula.limit(5, Inches.of(51), ARTERIAL)).isEqualTo(new Pounds(80000));
    }

    @Test
    void theGrossLimitCapsWhateverTheFormulaWouldAllow() {
        assertThat(Formula.limit(9, Inches.of(80), INTERSTATE)).isEqualTo(new Pounds(80000));
        assertThat(Formula.limit(9, Inches.of(80), ARTERIAL)).isEqualTo(new Pounds(100000));
    }

    @Test
    void theFormulaHasNothingToSayAboutOneAxle() {
        assertThatThrownBy(() -> Formula.limit(1, Inches.of(4), ARTERIAL)).isInstanceOf(Impossible.class);
    }

    @Test
    void aFiveAxleSemiAtEightyThousandNeedsTheException() {
        AxleStream stream = semi();
        Span tandems = run(stream, 1, 4);
        assertThat(tandems.extent()).isEqualTo(Inches.of(36));
        assertThat(Formula.limit(4, tandems.extent(), INTERSTATE)).isEqualTo(new Pounds(66000));
        assertThat(Formula.twoTandemException(stream, tandems)).isTrue();
        assertThat(Formula.failures(stream, INTERSTATE)).isEmpty();
    }

    @Test
    void theFindingThatUsedTheExceptionSaysWhichRuleLetItThrough() {
        List<Finding> findings = Formula.measure(semi(), INTERSTATE);
        List<Finding> used =
                findings.stream().filter(one -> one.exception().isPresent()).toList();
        assertThat(used).hasSize(1);
        assertThat(used.get(0).allowed()).isEqualTo(new Pounds(68000));
        assertThat(used.get(0).toString()).contains("under the two tandem exception");
    }

    @Test
    void theSameSemiWithTheTandemsPulledInIsOver() {
        AxleStream stream = semi(200);
        List<Finding> over = Formula.failures(stream, INTERSTATE);
        assertThat(over).isNotEmpty();
        assertThat(Formula.worst(stream, INTERSTATE).orElseThrow().span().count())
                .isEqualTo(4);
    }

    @Test
    void theExceptionDoesNotReachARunThatIsNotTwoTandems() {
        AxleStream stream = tractorLowboy();
        Map<List<Integer>, Span> spans =
                stream.spans().stream().collect(Collectors.toMap(FormulaTest::ends, Function.identity()));
        assertThat(Formula.twoTandemException(stream, spans.get(List.of(0, 3)))).isFalse();
        assertThat(Formula.twoTandemException(stream, spans.get(List.of(3, 5)))).isFalse();
    }

    @Test
    void theExceptionDoesNotReachTandemsThatAreOverOnTheirOwn() {
        AxleStream stream = make(List.of(12000, 19000, 19000, 19000, 19000), List.of(200, 54, 324, 54));
        Span tandems = run(stream, 1, 4);
        assertThat(Formula.twoTandemException(stream, tandems)).isFalse();
    }

    @Test
    void aLowboyTridemIsTheRunThatFailsFirst() {
        AxleStream stream = tractorLowboy();
        Map<List<Integer>, Finding> findings = Formula.measure(stream, ARTERIAL).stream()
                .collect(Collectors.toMap(one -> ends(one.span()), Function.identity()));
        Finding tridem = findings.get(List.of(3, 5));
        assertThat(tridem.allowed()).isEqualTo(new Pounds(43000));
        assertThat(tridem.weight()).isEqualTo(new Pounds(48000));
        assertThat(tridem.over()).isEqualTo(new Pounds(5000));
        assertThat(tridem.passes()).isFalse();
    }

    @Test
    void everyRunOverTheFormulaIsListedAndNothingElseIs() {
        List<Finding> over = Formula.failures(tractorLowboy(), ARTERIAL);
        assertThat(over)
                .extracting(one -> ends(one.span()))
                .containsExactly(
                        List.of(3, 5), List.of(1, 4), List.of(2, 5), List.of(0, 4), List.of(1, 5), List.of(0, 5));
    }

    @Test
    void theWorstRunIsTheOneFurthestOverAndNotTheFirstFound() {
        Finding finding = Formula.worst(tractorLowboy(), ARTERIAL).orElseThrow();
        assertThat(ends(finding.span())).isEqualTo(List.of(1, 5));
        assertThat(finding.over()).isEqualTo(new Pounds(16500));
    }

    @Test
    void aStreamThatClearsEverythingHasNoWorstRun() {
        AxleStream stream = make(List.of(12000, 17000, 17000), List.of(200, 54));
        assertThat(Formula.worst(stream, ARTERIAL)).isEmpty();
        assertThat(Formula.failures(stream, ARTERIAL)).isEmpty();
    }

    @Test
    void aFindingPrintsTheRunTheAllowanceAndTheVerdict() {
        Finding finding = Formula.worst(tractorLowboy(), ARTERIAL).orElseThrow();
        assertThat(finding).isInstanceOf(Finding.class);
        assertThat(finding)
                .hasToString("axles 2 to 6, 82,000 lb over 28 ft 2 in, allowed 65,500 lb, over by 16,500 lb");
    }
}
