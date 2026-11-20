package io.github.rkaushikdev.jeepdolly.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The fee schedule, its weight steps, and which one is in force. */
class ScheduleTest {

    private static final FeeSchedule SCHEDULE = FeeSchedule.standard();

    private static FeeSchedule withSteps(List<WeightStep> steps) {
        return new FeeSchedule(
                LocalDate.of(2025, 1, 1), Map.of(), Map.of(), steps, Dollars.of(200), Dollars.of(25), Dollars.of(40));
    }

    @Test
    void theBaseFeeIsReadByTheKindOfPermit() {
        assertThat(SCHEDULE.baseFor(PermitKind.ORDINARY)).isEqualTo(Dollars.of(40));
        assertThat(SCHEDULE.baseFor(PermitKind.STUDY)).isEqualTo(Dollars.of(125));
        assertThat(SCHEDULE.baseFor(PermitKind.SUPERLOAD)).isEqualTo(Dollars.of(350));
    }

    @Test
    void aPermitKindWithNoFeeIsRefusedRatherThanChargedNothing() {
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> SCHEDULE.baseFor(PermitKind.NONE));
        assertThat(refusal.facts().get("kind")).isEqualTo("none");
    }

    @Test
    void theMileRateIsDearestOnTheRoadsThatBreakFirst() {
        assertThat(SCHEDULE.mileRate(HighwayClass.INTERSTATE)).isEqualTo(new Dollars(12));
        assertThat(SCHEDULE.mileRate(HighwayClass.STATE_AID)).isEqualTo(new Dollars(26));
        assertThat(SCHEDULE.mileRate(HighwayClass.STATE_AID)).isGreaterThan(SCHEDULE.mileRate(HighwayClass.ARTERIAL));
    }

    @Test
    void noOverageIsNoWeightCharge() {
        assertThat(SCHEDULE.weightCharge(new Pounds(0))).isEqualTo(new Dollars(0));
        assertThat(SCHEDULE.weightCharge(new Pounds(-500))).isEqualTo(new Dollars(0));
    }

    @Test
    void aPartBlockIsChargedAsAWholeOne() {
        assertThat(SCHEDULE.weightCharge(new Pounds(1))).isEqualTo(Dollars.of(5));
        assertThat(SCHEDULE.weightCharge(new Pounds(1000))).isEqualTo(Dollars.of(5));
        assertThat(SCHEDULE.weightCharge(new Pounds(1001))).isEqualTo(Dollars.of(10));
    }

    @Test
    void theChargeClimbsThroughTheSteps() {
        assertThat(SCHEDULE.weightCharge(new Pounds(20000))).isEqualTo(Dollars.of(100));
        assertThat(SCHEDULE.weightCharge(new Pounds(25000))).isEqualTo(Dollars.of(140));
        assertThat(SCHEDULE.weightCharge(new Pounds(60000))).isEqualTo(Dollars.of(420));
        assertThat(SCHEDULE.weightCharge(new Pounds(100000))).isEqualTo(Dollars.of(900));
    }

    @Test
    void theStepBoundaryIsChargedAtTheLowerRate() {
        Dollars atEdge = SCHEDULE.weightCharge(new Pounds(20000));
        Dollars pastEdge = SCHEDULE.weightCharge(new Pounds(21000));
        assertThat(pastEdge.minus(atEdge)).isEqualTo(Dollars.of(8));
    }

    @Test
    void aStepPrintsTheWayThePublishedTableReads() {
        assertThat(SCHEDULE.steps().get(0)).hasToString("$5.00 per 1,000 lb to 20,000 lb");
        assertThat(SCHEDULE.steps().get(SCHEDULE.steps().size() - 1)).hasToString("$12.00 per 1,000 lb and above");
    }

    @Test
    void aScheduleWhoseLastStepHasACeilingIsRefused() {
        assertThatThrownBy(() -> withSteps(List.of(new WeightStep(new Pounds(20000), Dollars.of(5)))))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("no ceiling");
    }

    @Test
    void aScheduleWithStepsOutOfOrderIsRefused() {
        assertThatThrownBy(() -> withSteps(List.of(
                        new WeightStep(new Pounds(60000), Dollars.of(5)),
                        new WeightStep(new Pounds(20000), Dollars.of(8)),
                        WeightStep.andAbove(Dollars.of(12)))))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aScheduleWithNoStepsAtAllIsRefused() {
        assertThatThrownBy(() -> withSteps(List.of())).isInstanceOf(Impossible.class);
    }

    @Test
    void theScheduleInForceIsTheLatestOneThatHadStarted() {
        ScheduleBook book = new ScheduleBook();
        book.file(FeeSchedule.standard(LocalDate.of(2024, 1, 1)));
        book.file(FeeSchedule.standard(LocalDate.of(2025, 7, 1)));
        assertThat(book.on(LocalDate.of(2025, 3, 3)).effective()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(book.on(LocalDate.of(2025, 8, 3)).effective()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(book.on(LocalDate.of(2025, 8, 3))).hasToString("schedule of 2025-07-01");
    }

    @Test
    void twoSchedulesStartingOnOneDayIsRefused() {
        ScheduleBook book = new ScheduleBook();
        book.file(FeeSchedule.standard(LocalDate.of(2024, 1, 1)));
        assertThatThrownBy(() -> book.file(FeeSchedule.standard(LocalDate.of(2024, 1, 1))))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void askingBeforeAnyScheduleExistedSaysSo() {
        ScheduleBook book = new ScheduleBook();
        book.file(FeeSchedule.standard(LocalDate.of(2024, 1, 1)));
        assertThatThrownBy(() -> book.on(LocalDate.of(2023, 6, 1))).isInstanceOf(NotOnFile.class);
        assertThat(book.size()).isEqualTo(1);
    }
}
