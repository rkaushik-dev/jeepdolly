package io.github.rkaushikdev.jeepdolly.utilities;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Overhead lines, the six inches of daylight, and the notice nobody allows for. */
class LinesTest {

    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1");
    // A Wednesday, which matters because the notice is counted in working days.
    private static final LocalDate TRAVEL = LocalDate.of(2025, 4, 9);
    private static final LocalDate FILED = LocalDate.of(2025, 4, 1);

    private static Envelope load() {
        return load("13'6");
    }

    private static Envelope load(String height) {
        return Envelope.read("12'", height, "82'");
    }

    private static LineOwner anOwner() {
        return anOwner(5);
    }

    private static LineOwner anOwner(int notice) {
        return new LineOwner("CMP", "Central Maine Power", "207 555 0100", notice);
    }

    /** A crossing as the survey writes it; a null lift height is a wire that cannot be raised. */
    private static Crossing aCrossing(String ident, String segment, String height, String liftTo, String owner) {
        return new Crossing(
                ident,
                segment,
                "Fairfield village",
                Inches.parse(height),
                owner,
                Optional.ofNullable(liftTo).map(Inches::parse),
                "");
    }

    private static Crossing aCrossing() {
        return aCrossing("L-1", "US201-2", "18'", "24'", "CMP");
    }

    private static Crossing aCrossing(String height, String liftTo) {
        return aCrossing("L-1", "US201-2", height, liftTo, "CMP");
    }

    private static LineBook book(List<Crossing> crossings) {
        LineBook made = new LineBook();
        made.addOwner(anOwner());
        crossings.forEach(made::add);
        return made;
    }

    private static LineBook book() {
        return book(List.of());
    }

    private static Itinerary trip() {
        return new Itinerary(roadLog(), "waterville", ACROSS);
    }

    @Test
    void theOfficeWantsSixInchesOfDaylight() {
        assertThat(Lines.SAFETY).isEqualTo(Inches.of(0, 6));
        assertThat(Lines.wanted(load())).isEqualTo(Inches.of(14, 0));
    }

    @Test
    void anOwnerCannotWantNegativeNotice() {
        assertThatThrownBy(() -> new LineOwner("CMP", "Central Maine Power", "207 555 0100", -1))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void theNoticeIsCountedInWorkingDaysAndSkipsTheWeekend() {
        // Five working days before Wednesday the ninth is Wednesday the second.
        assertThat(anOwner().noticeBy(TRAVEL)).isEqualTo(LocalDate.of(2025, 4, 2));
    }

    @Test
    void anOwnerWantingNoNoticeCanBeRungOnTheDay() {
        assertThat(anOwner(0).noticeBy(TRAVEL)).isEqualTo(TRAVEL);
    }

    @Test
    void aRequestOnTheNoticeDayItselfIsInTime() {
        assertThat(anOwner().inTime(LocalDate.of(2025, 4, 2), TRAVEL)).isTrue();
        assertThat(anOwner().inTime(LocalDate.of(2025, 4, 3), TRAVEL)).isFalse();
    }

    @Test
    void theOwnerPrintsTheWayTheCounterReadsIt() {
        assertThat(anOwner()).hasToString("CMP  Central Maine Power  207 555 0100  5 days notice");
    }

    @Test
    void aCrossingWithNoMeasuredHeightIsRefused() {
        assertThatThrownBy(() -> aCrossing("0", "24'")).isInstanceOf(Conflict.class);
    }

    @Test
    void aCrossingThatLiftsToBelowWhereItHangsIsRefused() {
        assertThatThrownBy(() -> aCrossing("18'", "16'")).isInstanceOf(Conflict.class);
    }

    @Test
    void aCrossingSaysWhetherItCanBeLiftedAtAll() {
        assertThat(aCrossing().liftable()).isTrue();
        assertThat(aCrossing("18'", null).liftable()).isFalse();
    }

    @Test
    void aLoadWellUnderTheWireClearsIt() {
        assertThat(aCrossing().clears(load())).isTrue();
    }

    @Test
    void aLoadExactlyTheHeightOfTheWireDoesNotClearIt() {
        assertThat(aCrossing("13'6", "24'").clears(load())).isFalse();
    }

    @Test
    void theDaylightIsWhatDecidesACloseOne() {
        assertThat(aCrossing("14'", "24'").clears(load())).isTrue();
        assertThat(aCrossing("13'11", "24'").clears(load())).isFalse();
    }

    @Test
    void aLoadThatClearsOnceLiftedSaysSo() {
        Envelope tall = load("20'");
        assertThat(aCrossing().clears(tall)).isFalse();
        assertThat(aCrossing().clearsLifted(tall)).isTrue();
    }

    @Test
    void aLoadTooTallEvenForTheLiftSaysSo() {
        assertThat(aCrossing().clearsLifted(load("26'"))).isFalse();
    }

    @Test
    void aCrossingThatCannotBeLiftedNeverClearsLifted() {
        assertThat(aCrossing("18'", null).clearsLifted(load("12'"))).isFalse();
    }

    @Test
    void theCrossingPrintsWhatTheCardSays() {
        assertThat(aCrossing()).hasToString("L-1  US201-2 at Fairfield village  18 ft, lifts to 24 ft");
        assertThat(aCrossing("18'", null).toString()).endsWith("cannot be lifted");
    }

    @Test
    void aCrossingCannotBeFiledAgainstAnOwnerNobodyHas() {
        assertThatThrownBy(() -> book(List.of(aCrossing("L-1", "US201-2", "18'", "24'", "BHE"))))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void theSameCrossingIsNotFiledTwice() {
        LineBook made = book(List.of(aCrossing()));
        assertThatThrownBy(() -> made.add(aCrossing())).isInstanceOf(Conflict.class);
    }

    @Test
    void theSameOwnerIsNotFiledTwice() {
        LineBook made = book();
        assertThatThrownBy(() -> made.addOwner(anOwner())).isInstanceOf(Conflict.class);
    }

    @Test
    void aCrossingNobodyHasIsARefusalThatNamesIt() {
        assertThatThrownBy(() -> book().get("L-9")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void theCrossingsOfOneSegmentComeBackInIdentOrder() {
        LineBook made = book(List.of(
                aCrossing("L-2", "US201-2", "18'", "24'", "CMP"), aCrossing("L-1", "US201-2", "18'", "24'", "CMP")));
        assertThat(made.onSegment("US201-2")).extracting(Crossing::ident).containsExactly("L-1", "L-2");
        assertThat(made.onSegment("I95-1")).isEmpty();
    }

    @Test
    void theLowestWireIsTheOneTheOfficeWorriesAbout() {
        LineBook made = book(List.of(
                aCrossing("L-1", "US201-2", "18'", "24'", "CMP"), aCrossing("L-2", "US201-2", "16'", "24'", "CMP")));
        assertThat(made.lowest().orElseThrow().ident()).isEqualTo("L-2");
        assertThat(made.size()).isEqualTo(2);
    }

    @Test
    void anEmptyBookHasNoLowestWire() {
        assertThat(book().lowest()).isEmpty();
    }

    @Test
    void aRouteWithNoWiresOnItIsClear() {
        LineReading found = Lines.under(trip(), load("20'"), book(), FILED, TRAVEL);
        assertThat(found.clear()).isTrue();
        assertThat(found.lifts()).isEmpty();
        assertThat(found).hasToString("nothing overhead");
    }

    @Test
    void aLoadThatClearsEverythingNeedsNobodyRung() {
        LineReading found = Lines.under(trip(), load(), book(List.of(aCrossing())), FILED, TRAVEL);
        assertThat(found.clear()).isTrue();
        assertThat(found.lifts()).isEmpty();
    }

    @Test
    void aLoadThatNeedsALiftGetsANameAndANumber() {
        LineReading found = Lines.under(trip(), load("20'"), book(List.of(aCrossing())), FILED, TRAVEL);
        assertThat(found.clear()).isTrue();
        assertThat(found.lifts()).hasSize(1);
        assertThat(found.lifts().get(0))
                .hasToString("US201-2 at Fairfield village: ring Central Maine Power on 207 555 0100 by 2025-04-02");
        assertThat(found).hasToString("1 lifts to arrange");
    }

    @Test
    void aLiftAskedForTooLateIsARefusalThatSaysItInDays() {
        LocalDate late = LocalDate.of(2025, 4, 7);
        LineReading found = Lines.under(trip(), load("20'"), book(List.of(aCrossing())), late, TRAVEL);
        assertThat(found.clear()).isFalse();
        assertThat(found.refusals())
                .containsExactly("US201-2 at Fairfield village wants 5 working days notice to Central Maine Power");
    }

    @Test
    void aWireNothingCanBeDoneAboutIsARefusalThatQuotesBothHeights() {
        LineBook made = book(List.of(aCrossing("18'", null)));
        LineReading found = Lines.under(trip(), load("20'"), made, FILED, TRAVEL);
        assertThat(found.refusals())
                .containsExactly("US201-2 at Fairfield village hangs at 18 ft and this load wants 20 ft 6 in");
    }

    @Test
    void aWireOnASegmentTheRouteMissesIsNobodysBusiness() {
        LineBook made = book(List.of(aCrossing("L-1", "SR8-1", "18'", null, "CMP")));
        assertThat(Lines.under(trip(), load("20'"), made, FILED, TRAVEL).clear())
                .isTrue();
    }

    @Test
    void twoWiresOnOneRouteBothComeBack() {
        LineBook made = book(List.of(aCrossing(), aCrossing("L-2", "US2-1", "17'", "24'", "CMP")));
        LineReading found = Lines.under(trip(), load("20'"), made, FILED, TRAVEL);
        assertThat(found.lifts()).hasSize(2);
    }

    @Test
    void aRouteWithOneLiftAndOneRefusalReportsBoth() {
        LineBook made = book(List.of(aCrossing(), aCrossing("L-2", "US2-1", "17'", null, "CMP")));
        LineReading found = Lines.under(trip(), load("20'"), made, FILED, TRAVEL);
        assertThat(found.lifts()).hasSize(1);
        assertThat(found.blocked()).hasSize(1);
        assertThat(found).hasToString("1 lines in the way");
    }

    @Test
    void aLiftTheOwnerCanReachSaysItIsPossible() {
        LineReading found = Lines.under(trip(), load("20'"), book(List.of(aCrossing())), FILED, TRAVEL);
        assertThat(found.lifts().get(0).possible()).isTrue();
    }
}
