package io.github.rkaushikdev.jeepdolly.domain;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Restrictions, the six kinds of them, and when they are in force. */
class RestrictionTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final LocalDate JULY = LocalDate.of(2025, 7, 1);
    private static final Set<DayOfWeek> WEEKDAYS = Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
    private static final Inches LOW_BRIDGE = Inches.of(13, 9);

    private static Restriction clearance() {
        return clearance("R-1", "US2-1", LOW_BRIDGE);
    }

    private static Restriction clearance(String ident, String segment, Inches height) {
        return Restriction.builder(ident, segment, RestrictionKind.CLEARANCE, "Norridgewock railroad bridge")
                .height(height)
                .build();
    }

    private static Restriction.Builder closure(String ident, String segment) {
        return Restriction.builder(ident, segment, RestrictionKind.CLOSURE, "culvert replacement");
    }

    private static Restriction.Builder closure() {
        return closure("R-9", "SA148-1");
    }

    @Test
    void aClearanceCarriesTheHeightAndTheThingItIsUnder() {
        Restriction one = clearance();
        assertThat(one.height()).contains(new Inches(165));
        assertThat(one.kind()).isSameAs(RestrictionKind.CLEARANCE);
        assertThat(one).hasToString("US2-1 clearance 13 ft 9 in: Norridgewock railroad bridge");
    }

    @Test
    void aPostedStructureCarriesAWeightAndAClosureCarriesNothing() {
        Restriction posted = Restriction.builder("R-2", "SA148-2", RestrictionKind.WEIGHT, "Carrabassett bridge")
                .weight(new Pounds(48000))
                .build();
        assertThat(posted.weight()).contains(new Pounds(48000));
        assertThat(closure().build()).hasToString("SA148-1 closure: culvert replacement");
        assertThat(closure().build().kind().stopsEverything()).isTrue();
        assertThat(posted.kind().stopsEverything()).isFalse();
    }

    @Test
    void aRestrictionWithoutTheNumberItComparesAgainstIsRefused() {
        assertThatThrownBy(() -> new Restriction("R-3", "US2-1", RestrictionKind.CLEARANCE, "no number on it"))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("compare against");
        assertThatThrownBy(() -> new Restriction("R-4", "US2-1", RestrictionKind.WEIGHT, "posted, but no number"))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(
                        () -> new Restriction("R-5", "US2-1", RestrictionKind.SEASONAL, "posted road, but no region"))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aSeasonalRestrictionNamesTheRegionAndNotAWeight() {
        Restriction posted = Restriction.builder("R-6", "SA148-1", RestrictionKind.SEASONAL, "spring posting")
                .region("district 4")
                .build();
        assertThat(posted.region()).contains("district 4");
        assertThat(posted.weight()).isEmpty();
        assertThat(posted.toString()).endsWith("seasonal district 4: spring posting");
    }

    @Test
    void aPermanentRestrictionAppliesOnAnyDayAtAll() {
        Restriction one = clearance();
        assertThat(one.permanent()).isTrue();
        assertThat(one.appliesOn(MARCH)).isTrue();
        assertThat(one.appliesOn(LocalDate.of(2031, 1, 1))).isTrue();
    }

    @Test
    void aDatedRestrictionAppliesOnlyInsideItsDates() {
        Restriction work = closure()
                .effective(LocalDate.of(2025, 6, 1))
                .expires(LocalDate.of(2025, 8, 15))
                .build();
        assertThat(work.permanent()).isFalse();
        assertThat(work.appliesOn(MARCH)).isFalse();
        assertThat(work.appliesOn(JULY)).isTrue();
        assertThat(work.appliesOn(LocalDate.of(2025, 9, 1))).isFalse();
    }

    @Test
    void aRestrictionThatLiftsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> closure()
                        .effective(LocalDate.of(2025, 8, 1))
                        .expires(LocalDate.of(2025, 6, 1))
                        .build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aRestrictionWithOnlyAStartNeverLifts() {
        Restriction work = closure().effective(LocalDate.of(2025, 6, 1)).build();
        assertThat(work.appliesOn(MARCH)).isFalse();
        assertThat(work.appliesOn(LocalDate.of(2030, 1, 1))).isTrue();
    }

    @Test
    void aCurfewCoversTheDaysAndTheHoursItNames() {
        Curfew rush = new Curfew(WEEKDAYS, LocalTime.of(6, 30), LocalTime.of(8, 30));
        assertThat(rush.covers(LocalDate.of(2025, 3, 3), LocalTime.of(7, 0))).isTrue();
        assertThat(rush.covers(LocalDate.of(2025, 3, 3), LocalTime.of(9, 0))).isFalse();
        assertThat(rush.covers(LocalDate.of(2025, 3, 8), LocalTime.of(7, 0))).isFalse();
        assertThat(rush).hasToString("MTWTF 06:30 to 08:30");
    }

    @Test
    void aCurfewWithNoDaysOrABackwardsClockIsRefused() {
        assertThatThrownBy(() -> new Curfew(Set.of(), LocalTime.of(6, 30), LocalTime.of(8, 30)))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Curfew(WEEKDAYS, LocalTime.of(8, 30), LocalTime.of(6, 30)))
                .isInstanceOf(Impossible.class);
        // A curfew's days are days of the week by type, so a ninth day cannot be written down.
        assertThat(Curfew.class.getRecordComponents()[0].getGenericType().getTypeName())
                .isEqualTo("java.util.Set<java.time.DayOfWeek>");
    }

    @Test
    void theBookHandsBackWhatIsInForceOnASegmentThatDay() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance());
        book.add(closure("R-8", "US2-1").effective(LocalDate.of(2025, 6, 1)).build());
        assertThat(book.onSegment("US2-1", MARCH)).hasSize(1);
        assertThat(book.onSegment("US2-1", JULY)).hasSize(2);
        assertThat(book.onSegment("SR43-1", MARCH)).isEmpty();
    }

    @Test
    void theBookListsOneKindAtATimeForTheCalendar() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance());
        book.add(clearance("R-2", "SR43-1", Inches.of(14, 2)));
        book.add(closure().build());
        assertThat(book.ofKind(RestrictionKind.CLEARANCE, MARCH))
                .extracting(Restriction::ident)
                .containsExactly("R-1", "R-2");
        assertThat(book.closures(MARCH)).extracting(Restriction::ident).containsExactly("R-9");
    }

    @Test
    void aRestrictionCanBeLiftedWhenTheWorkFinishesEarly() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance());
        book.add(closure().build());
        assertThat(book.size()).isEqualTo(2);
        book.lift("R-9");
        assertThat(book.size()).isEqualTo(1);
        assertThat(book.onSegment("SA148-1", MARCH)).isEmpty();
        assertThatThrownBy(() -> book.lift("R-9")).isInstanceOf(Conflict.class);
    }

    @Test
    void theSameRestrictionMayNotBeFiledTwice() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance());
        assertThatThrownBy(() -> book.add(clearance())).isInstanceOf(Conflict.class);
    }

    @Test
    void theRestrictedSegmentsOfADayAreWhatTheRouterAvoids() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance());
        book.add(closure().effective(LocalDate.of(2025, 6, 1)).build());
        assertThat(book.segmentsRestricted(MARCH)).containsExactly("US2-1");
        assertThat(book.segmentsRestricted(JULY)).containsExactly("SA148-1", "US2-1");
    }
}
