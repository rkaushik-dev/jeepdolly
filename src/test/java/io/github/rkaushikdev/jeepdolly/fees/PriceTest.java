package io.github.rkaushikdev.jeepdolly.fees;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Mileage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pricing a permit line by line, and what an amendment costs. */
class PriceTest {

    private static final FeeSchedule SCHEDULE = FeeSchedule.standard();
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");

    private static Mileage miles(List<String> idents, String origin) {
        return Mileage.chargeable(new Itinerary(roadLog(), origin, idents));
    }

    private static Quote quote() {
        return quote(PermitKind.ORDINARY, 0, false);
    }

    private static Quote quote(PermitKind kind, int overage, boolean review) {
        return Pricing.price(SCHEDULE, kind, miles(ACROSS, "waterville"), new Pounds(overage), review);
    }

    @Test
    void aPlainPermitIsTheBaseFeeAndTheMileage() {
        Quote found = quote();
        assertThat(found.size()).isEqualTo(3);
        assertThat(found.lines().get(0).description()).isEqualTo("ordinary permit");
        assertThat(found.lines().get(0).amount()).isEqualTo(Dollars.of(40));
        assertThat(found.lines().get(1).description()).isEqualTo("34 miles on arterial");
        assertThat(found.lines().get(1).amount()).isEqualTo(Dollars.of(6, 12));
        assertThat(found.lines().get(2).amount()).isEqualTo(Dollars.of(2, 60));
        assertThat(found.total()).isEqualTo(Dollars.of(48, 72));
    }

    @Test
    void eachClassOfRoadGetsItsOwnLineAtItsOwnRate() {
        Quote found = quote();
        assertThat(found.line("34 miles on arterial")).isPresent();
        FeeLine stateAid = found.line("10 miles on state aid").orElseThrow();
        assertThat(stateAid.amount()).isEqualTo(Dollars.of(2, 60));
    }

    @Test
    void aClassWithNoRateGetsNoLineAtAll() {
        Quote found = Pricing.price(
                SCHEDULE,
                PermitKind.ORDINARY,
                miles(List.of("US201-3", "TR104-1", "SR8-1"), "skowhegan"),
                new Pounds(0),
                false);
        assertThat(found.line("3 miles on town way")).isEmpty();
    }

    @Test
    void theWeightChargeIsALineWhenThereIsAnOverage() {
        Quote found = quote(PermitKind.ORDINARY, 25000, false);
        assertThat(found.line("25,000 lb over legal gross")).isPresent();
        assertThat(found.total()).isEqualTo(Dollars.of(188, 72));
    }

    @Test
    void noOverageMeansNoWeightLine() {
        assertThat(quote().line("0 lb over legal gross")).isEmpty();
    }

    @Test
    void aStudyCostsMoreThanAnOrdinaryPermitBeforeAnythingElse() {
        Quote ordinary = quote();
        Quote study = quote(PermitKind.STUDY, 0, false);
        assertThat(study.total().minus(ordinary.total())).isEqualTo(Dollars.of(85));
    }

    @Test
    void aReviewAddsItsOwnLineAndSaysWhatItIs() {
        Quote found = quote(PermitKind.SUPERLOAD, 0, true);
        assertThat(found.line("engineering review")).isPresent();
        assertThat(found.line("engineering review").orElseThrow().amount()).isEqualTo(Dollars.of(200));
    }

    @Test
    void aShortTripIsChargedUpToTheMinimumMileage() {
        Quote found = Pricing.price(SCHEDULE, PermitKind.ORDINARY, miles(List.of("US2-1"), "skowhegan"), new Pounds(0));
        assertThat(found.line("7 miles on arterial")).isPresent();
        FeeLine shortBy = found.line("3 miles to the minimum mileage").orElseThrow();
        assertThat(shortBy.amount()).isEqualTo(Dollars.of(0, 54));
        assertThat(found.total()).isEqualTo(Dollars.of(41, 80));
    }

    @Test
    void aCheapPermitIsWrittenUpToTheMinimumFee() {
        FeeSchedule cheap = new FeeSchedule(
                SCHEDULE.effective(),
                Map.of(PermitKind.ORDINARY, Dollars.of(10)),
                SCHEDULE.perMile(),
                SCHEDULE.steps(),
                SCHEDULE.review(),
                SCHEDULE.amendment(),
                SCHEDULE.minimum());
        Quote found = Pricing.price(cheap, PermitKind.ORDINARY, miles(List.of("US2-1"), "skowhegan"), new Pounds(0));
        assertThat(found.minimumApplied()).isTrue();
        assertThat(found.total()).isEqualTo(Dollars.of(40));
        assertThat(found.lines().get(found.size() - 1).description()).isEqualTo("minimum fee adjustment");
    }

    @Test
    void theQuoteSaysWhichScheduleItWasPricedOff() {
        assertThat(quote().schedule()).isEqualTo("schedule of 2024-01-01");
        assertThat(quote()).hasToString("3 lines, $48.72");
    }

    @Test
    void anAmendmentIsTheFlatFeeWhenNothingGotDearer() {
        Quote before = quote(PermitKind.ORDINARY, 25000, false);
        Quote after = quote();
        Quote found = Pricing.amendment(SCHEDULE, before, after);
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.total()).isEqualTo(Dollars.of(25));
    }

    @Test
    void anAmendmentThatCostsMoreChargesTheDifferenceAsWell() {
        Quote before = quote();
        Quote after = quote(PermitKind.ORDINARY, 25000, false);
        Quote found = Pricing.amendment(SCHEDULE, before, after);
        assertThat(found.size()).isEqualTo(2);
        assertThat(found.line("difference in permit fee").orElseThrow().amount())
                .isEqualTo(Dollars.of(140));
        assertThat(found.total()).isEqualTo(Dollars.of(165));
    }

    @Test
    void aLinePrintsTheWayTheReceiptPrintsIt() {
        assertThat(quote().lines().get(0)).hasToString("ordinary permit  $40.00");
    }
}
