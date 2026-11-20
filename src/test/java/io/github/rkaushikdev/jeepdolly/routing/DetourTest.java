package io.github.rkaushikdev.jeepdolly.routing;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Repairing the route the hauler wrote rather than replacing it. */
class DetourTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final List<String> WRITTEN = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");
    private static final Search LOAD = new Search(Envelope.read("12'", "13'9", "82'"), new Pounds(94000), MARCH);

    private static RestrictionBook closed(String segment) {
        RestrictionBook book = new RestrictionBook();
        book.add(new Restriction("R-1", segment, RestrictionKind.CLOSURE, "bridge out"));
        return book;
    }

    private static Itinerary written(Network network) {
        return new Itinerary(network, "waterville", WRITTEN);
    }

    private static Optional<Repair> mend(RestrictionBook restrictions) {
        Network network = roadLog();
        return Detour.repair(network, written(network), LOAD, restrictions, new SeasonBook(), StatutoryBook.standard());
    }

    @Test
    void aRouteWithNothingWrongNeedsNoRepair() {
        Network network = roadLog();
        assertThat(Detour.firstTrouble(
                        written(network), LOAD, new RestrictionBook(), new SeasonBook(), StatutoryBook.standard()))
                .isEmpty();
        assertThat(mend(new RestrictionBook())).isEmpty();
    }

    @Test
    void theFirstLegThatCannotBeDrivenIsTheOneReported() {
        Network network = roadLog();
        RestrictionBook book = closed("SA148-1");
        assertThat(Detour.firstTrouble(written(network), LOAD, book, new SeasonBook(), StatutoryBook.standard()))
                .hasValue(3);
    }

    @Test
    void aClosureInTheMiddleIsGoneRoundAndTheGoodPartIsKept() {
        Repair mended = mend(closed("SA148-1")).orElseThrow();
        assertThat(mended.repaired().idents()).containsExactly("US201-1", "US201-2", "SA148-2", "SR43-1");
        assertThat(mended.dropped()).containsExactly("US2-1", "SA148-1");
        assertThat(mended.added()).containsExactly("SA148-2");
    }

    @Test
    void theRepairSaysHowMuchFurtherItMadeTheTrip() {
        Repair mended = mend(closed("SA148-1")).orElseThrow();
        assertThat(mended.original().distance()).isEqualTo(Miles.of(43, 20));
        assertThat(mended.repaired().distance()).isEqualTo(Miles.of(35, 20));
        assertThat(mended.extra()).isEqualTo(Miles.of(-8));
    }

    @Test
    void aRepairPrintsWhatWasDroppedAndWhatReplacedIt() {
        Repair mended = mend(closed("SA148-1")).orElseThrow();
        assertThat(mended).hasToString("round US2-1, SA148-1 by SA148-2, 8.00 mi shorter");
    }

    @Test
    void aClosureAtTheVeryStartReplacesTheWholeRoute() {
        Repair mended = mend(closed("US201-1")).orElseThrow();
        assertThat(mended.repaired().idents()).containsExactly("I95-1", "I95-2");
        assertThat(mended.repaired().origin()).isEqualTo("waterville");
        assertThat(mended.repaired().destination()).isEqualTo("pittsfield");
    }

    @Test
    void aClosureWithNothingBeyondItCannotBeRepaired() {
        RestrictionBook book = closed("SR43-1");
        book.add(new Restriction("R-2", "I95-2", RestrictionKind.CLOSURE, "bridge out"));
        assertThat(mend(book)).isEmpty();
    }

    @Test
    void aClearanceTheLoadWillNotFitUnderIsRepairedLikeAClosure() {
        RestrictionBook book = new RestrictionBook();
        book.add(Restriction.builder("R-3", "SA148-1", RestrictionKind.CLEARANCE, "stone arch")
                .height(Inches.of(12))
                .build());
        Repair mended = mend(book).orElseThrow();
        assertThat(mended.dropped()).contains("SA148-1");
    }

    @Test
    void aLoadThatFitsUnderTheArchLeavesTheRouteAlone() {
        RestrictionBook book = new RestrictionBook();
        book.add(Restriction.builder("R-3", "SA148-1", RestrictionKind.CLEARANCE, "stone arch")
                .height(Inches.of(15))
                .build());
        assertThat(mend(book)).isEmpty();
    }

    @Test
    void aRepairedRouteStillJoinsEndToEnd() {
        Repair mended = mend(closed("US2-1")).orElseThrow();
        List<Leg> legs = mended.repaired().legs();
        assertThat(legs.get(0).enteredAt()).isEqualTo("waterville");
        assertThat(legs.get(legs.size() - 1).leftAt()).isEqualTo("pittsfield");
    }
}
