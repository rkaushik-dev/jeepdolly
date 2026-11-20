package io.github.rkaushikdev.jeepdolly.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What a violation costs, band by band. */
class PenaltyTest {

    private static final PenaltySchedule SCHEDULE = PenaltySchedule.standard();

    private static Pounds over(int pounds) {
        return new Pounds(pounds);
    }

    @Test
    void theOffencesSayWhichArePricedByWeight() {
        assertThat(Offence.OVERWEIGHT.byWeight()).isTrue();
        assertThat(Offence.NO_PERMIT.byWeight()).isFalse();
        assertThat(Offence.OFF_ROUTE).hasToString("off route");
    }

    @Test
    void aBandThatChargesNothingIsNotABand() {
        assertThatThrownBy(() -> new Band(new Pounds(5000), 0)).isInstanceOf(Impossible.class);
    }

    @Test
    void aBandPrintsTheWayTheStatuteReads() {
        assertThat(new Band(new Pounds(5000), 3)).hasToString("to 5,000 lb at 3c a pound");
        assertThat(Band.toTheTop(15)).hasToString("and over at 15c a pound");
    }

    @Test
    void aScheduleWithNoBandsIsRefused() {
        assertThatThrownBy(() -> new PenaltySchedule(LocalDate.of(2024, 1, 1), Dollars.of(50), List.of(), Map.of()))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aScheduleWhoseLastBandHasACeilingIsRefused() {
        assertThatThrownBy(() -> new PenaltySchedule(
                        LocalDate.of(2024, 1, 1), Dollars.of(50), List.of(new Band(new Pounds(5000), 3)), Map.of()))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aScheduleWithACeilingMissingInTheMiddleIsRefused() {
        List<Band> bands = List.of(Band.toTheTop(3), Band.toTheTop(5));
        assertThatThrownBy(() -> new PenaltySchedule(LocalDate.of(2024, 1, 1), Dollars.of(50), bands, Map.of()))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void nothingOverCostsNothingBeforeTheMinimumIsRead() {
        assertThat(Penalty.byWeight(SCHEDULE, over(0))).isEqualTo(Dollars.of(0));
    }

    @Test
    void theFirstBandIsChargedAtItsOwnRate() {
        assertThat(Penalty.byWeight(SCHEDULE, over(4000))).isEqualTo(Dollars.of(120));
    }

    @Test
    void theBandsAreCumulativeAndNotALookup() {
        // Five thousand at three cents, then two thousand at five.
        assertThat(Penalty.byWeight(SCHEDULE, over(7000))).isEqualTo(Dollars.of(250));
    }

    @Test
    void aLoadInTheThirdBandPaysAllThree() {
        // 5000 at 3c, 5000 at 5c, 2000 at 9c.
        assertThat(Penalty.byWeight(SCHEDULE, over(12000))).isEqualTo(Dollars.of(580));
    }

    @Test
    void aLoadPastTheLastCeilingPaysTheOpenBandOnTheRest() {
        // 150 + 250 + 900 + 5000 at 15c.
        assertThat(Penalty.byWeight(SCHEDULE, over(25000))).isEqualTo(Dollars.of(2050));
    }

    @Test
    void exactlyOnABandEdgeDoesNotSpillIntoTheNextOne() {
        assertThat(Penalty.byWeight(SCHEDULE, over(5000))).isEqualTo(Dollars.of(150));
        assertThat(Penalty.byWeight(SCHEDULE, over(10000))).isEqualTo(Dollars.of(400));
    }

    @Test
    void aSmallOverageIsLiftedToTheMinimum() {
        Assessed found = Penalty.assess(SCHEDULE, Offence.OVERWEIGHT, over(200));
        assertThat(found.total()).isEqualTo(Dollars.of(50));
    }

    @Test
    void anOverweightCitationWithNoOverageIsAMistake() {
        assertThatThrownBy(() -> Penalty.assess(SCHEDULE, Offence.OVERWEIGHT)).isInstanceOf(Impossible.class);
    }

    @Test
    void aFlatOffenceIsPricedOffTheTable() {
        assertThat(Penalty.assess(SCHEDULE, Offence.NO_PERMIT).total()).isEqualTo(Dollars.of(500));
        assertThat(Penalty.assess(SCHEDULE, Offence.NO_MARKING).total()).isEqualTo(Dollars.of(100));
    }

    @Test
    void anOveragePassedWithAFlatOffenceIsIgnored() {
        Assessed found = Penalty.assess(SCHEDULE, Offence.OFF_ROUTE, over(9000));
        assertThat(found.total()).isEqualTo(Dollars.of(250));
        assertThat(found.over()).isEmpty();
    }

    @Test
    void anOffenceTheScheduleDoesNotPriceIsARefusal() {
        PenaltySchedule thin =
                new PenaltySchedule(LocalDate.of(2024, 1, 1), Dollars.of(50), List.of(Band.toTheTop(3)), Map.of());
        assertThatThrownBy(() -> Penalty.assess(thin, Offence.NO_PERMIT)).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aFirstOffenceIsChargedOnce() {
        assertThat(Penalty.repeatsMultiplier(0)).isEqualTo(1);
    }

    @Test
    void aSecondOffenceInsideTheYearDoubles() {
        assertThat(Penalty.repeatsMultiplier(1)).isEqualTo(2);
        assertThat(Penalty.assess(SCHEDULE, Offence.NO_PERMIT, 1).total()).isEqualTo(Dollars.of(1000));
    }

    @Test
    void aThirdOffenceTreblesAndItStopsThere() {
        assertThat(Penalty.repeatsMultiplier(2)).isEqualTo(Penalty.MOST_REPEATS);
        assertThat(Penalty.repeatsMultiplier(9)).isEqualTo(Penalty.MOST_REPEATS);
    }

    @Test
    void aNegativeCountOfPriorsIsRefused() {
        assertThatThrownBy(() -> Penalty.repeatsMultiplier(-1)).isInstanceOf(Impossible.class);
    }

    @Test
    void theAssessmentShowsItsWorking() {
        Assessed found = Penalty.assess(SCHEDULE, Offence.OVERWEIGHT, over(12000), 1);
        assertThat(found.lines())
                .containsExactly(
                        "overweight  $580.00", "  over by  12,000 lb", "  repeat offence, times 2", "  due  $1,160.00");
        assertThat(found).hasToString("overweight: $1,160.00");
    }

    @Test
    void aFirstOffenceDoesNotPrintAMultiplierLine() {
        Assessed found = Penalty.assess(SCHEDULE, Offence.NO_ESCORT);
        assertThat(found.lines()).containsExactly("escorts not present  $350.00", "  due  $350.00");
    }

    @Test
    void theAssessmentKeepsTheOffenceItWasAskedAbout() {
        assertThat(Penalty.assess(SCHEDULE, Offence.IN_CURFEW)).isInstanceOf(Assessed.class);
        assertThat(Penalty.assess(SCHEDULE, Offence.IN_CURFEW).offence()).isSameAs(Offence.IN_CURFEW);
    }

    @Test
    void theScheduleSaysWhenItCameIntoForce() {
        assertThat(SCHEDULE).hasToString("penalties of 2024-01-01");
        assertThat(PenaltySchedule.standard(LocalDate.of(2026, 1, 1)).effective())
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }
}
