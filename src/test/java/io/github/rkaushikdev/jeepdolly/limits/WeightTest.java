package io.github.rkaushikdev.jeepdolly.limits;

import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One reading of a stream against the gross, the groups, the formula, the tires. */
class WeightTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final SeasonOrder THAW =
            new SeasonOrder("25-04", "district 3", SeasonKind.THAW, MARCH, LocalDate.of(2025, 5, 2), 65);

    private static StatutoryTable table() {
        return table(HighwayClass.ARTERIAL);
    }

    private static StatutoryTable table(HighwayClass highwayClass) {
        return StatutoryBook.standard().on(highwayClass, MARCH);
    }

    private static AxleStream legalSemi() {
        return make(List.of(12000, 17000, 17000, 17000, 17000), List.of(200, 54, 324, 54));
    }

    private static AxleStream sixAxles() {
        return make(List.of(12000, 20000, 20000, 20000, 20000, 20000), List.of(200, 54, 240, 54, 54));
    }

    @Test
    void aLegalSemiReadsLegalOnEveryRule() {
        WeightReading reading = WeightReading.read(legalSemi(), table());
        assertThat(reading.legal()).isTrue();
        assertThat(reading.overweight()).isFalse();
        assertThat(reading.reasons()).isEmpty();
        assertThat(reading).hasToString("legal weight");
    }

    @Test
    void theLowboyReadsOverweightOnTheFormulaAndNothingElse() {
        WeightReading reading = WeightReading.read(tractorLowboy(), table());
        assertThat(reading.overweight()).isTrue();
        assertThat(reading.grossOver()).isEqualTo(new Pounds(0));
        assertThat(reading.groupFailures()).isEmpty();
        assertThat(reading.formulaFailures()).hasSize(6);
    }

    @Test
    void theSameLowboyMissesAGroupLimitOnASecondaryRoad() {
        WeightReading reading = WeightReading.read(tractorLowboy(), table(HighwayClass.STATE_AID));
        assertThat(reading.groupFailures()).hasSize(1);
        assertThat(reading.groupFailures().get(0).group().name()).isEqualTo("tridem");
        assertThat(reading.groupFailures().get(0).over()).isEqualTo(new Pounds(4000));
    }

    @Test
    void theGroupFindingReadsAsALineOfTheLetter() {
        WeightReading reading = WeightReading.read(tractorLowboy(), table(HighwayClass.STATE_AID));
        assertThat(reading.groupFailures().get(0))
                .hasToString("group 3, tridem 48,000 lb, allowed 44,000 lb, over by 4,000 lb");
    }

    @Test
    void theGrossIsReportedAgainstTheRoadItIsOn() {
        AxleStream stream = sixAxles();
        WeightReading onState = WeightReading.read(stream, table());
        WeightReading onInterstate = WeightReading.read(stream, table(HighwayClass.INTERSTATE));
        assertThat(stream.gross()).isEqualTo(new Pounds(112000));
        assertThat(onState.grossOver()).isEqualTo(new Pounds(12000));
        assertThat(onInterstate.grossOver()).isEqualTo(new Pounds(32000));
    }

    @Test
    void aNarrowTireIsCaughtEvenWhenTheGroupIsInsideItsLimit() {
        AxleStream stream = make(List.of(12000, 17000, 17000), List.of(200, 54));
        WeightReading thin = WeightReading.read(stream, table(HighwayClass.INTERSTATE));
        assertThat(thin.tireFailures()).isEmpty();
        AxleStream heavy = make(List.of(12000, 30000, 30000), List.of(200, 54));
        WeightReading reading = WeightReading.read(heavy, table(HighwayClass.INTERSTATE));
        assertThat(reading.tireFailures()).hasSize(2);
        assertThat(reading.tireFailures().get(0)).hasToString("axle 2 carries 30,000 lb on tires rated 22,000 lb");
    }

    @Test
    void aThawOrderCutsTheGroupAndGrossLimitsTogether() {
        AxleStream stream = legalSemi();
        WeightReading openRoad = WeightReading.read(stream, table());
        WeightReading posted = WeightReading.read(stream, table(), Optional.of(THAW));
        assertThat(openRoad.legal()).isTrue();
        assertThat(posted.overweight()).isTrue();
        assertThat(posted.grossLimit()).isEqualTo(new Pounds(65000));
        assertThat(posted.grossOver()).isEqualTo(new Pounds(15000));
        assertThat(posted.order()).containsSame(THAW);
    }

    @Test
    void aThawOrderDoesNotMoveTheFormulaBecauseBridgesDoNotThaw() {
        AxleStream stream = legalSemi();
        WeightReading posted = WeightReading.read(stream, table(), Optional.of(THAW));
        Finding tandems = posted.formula().stream()
                .filter(one -> one.span().first() == 1 && one.span().last() == 4)
                .findFirst()
                .orElseThrow();
        assertThat(tandems.allowed().equals(new Pounds(65000))
                        || tandems.exception().equals(Optional.of("two tandem exception")))
                .isTrue();
        WeightReading formulaOnly = WeightReading.read(stream, table());
        assertThat(formulaOnly.formula()).hasSameSizeAs(posted.formula());
    }

    @Test
    void theWorstOverageIsWhatTheFeeWillBePricedOn() {
        WeightReading reading = WeightReading.read(tractorLowboy(), table());
        assertThat(reading.worstOverage()).isEqualTo(new Pounds(16500));
    }

    @Test
    void theReasonsLeadWithTheGrossAndThenWalkTheRules() {
        WeightReading reading = WeightReading.read(sixAxles(), table());
        String first = reading.reasons().get(0);
        assertThat(first).isEqualTo("gross 112,000 lb is 12,000 lb over 100,000 lb");
        assertThat(reading.reasons()).anyMatch(line -> line.startsWith("group"));
    }

    @Test
    void aLegalReadingHasNothingToSayAndSaysItQuickly() {
        WeightReading reading = WeightReading.read(legalSemi(), table());
        assertThat(reading.groupFailures()).isEmpty();
        assertThat(reading.formulaFailures()).isEmpty();
        assertThat(reading.tireFailures()).isEmpty();
        assertThat(reading.worstOverage()).isEqualTo(new Pounds(0));
    }
}
