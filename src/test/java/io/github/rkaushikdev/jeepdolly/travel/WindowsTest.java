package io.github.rkaushikdev.jeepdolly.travel;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.PermitCalendar;
import io.github.rkaushikdev.jeepdolly.domain.Curfew;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What is left of a week once every rule about when has been read. */
class WindowsTest {

    private static final PermitCalendar YEAR = new PermitCalendar(2025);
    private static final Set<DayOfWeek> WEEKDAYS = Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
    private static final LocalDate JUNE = LocalDate.of(2025, 6, 16);

    private static Envelope load() {
        return load("10'", "13'6");
    }

    private static Envelope load(String width, String height) {
        return Envelope.read(width, height, "80'");
    }

    private static Envelope wide(String width) {
        return load(width, "13'6");
    }

    @Test
    void anOrdinaryLoadCarriesNoWhenRulesAtAll() {
        TravelLimits limits = Windows.limitsFor(load());
        assertThat(limits.any()).isFalse();
        assertThat(limits).hasToString("no travel restriction");
    }

    @Test
    void aWideLoadMovesInDaylightAndSaysWhy() {
        TravelLimits limits = Windows.limitsFor(wide("13'"));
        assertThat(limits.daylightOnly()).isTrue();
        assertThat(limits.noWeekend()).isFalse();
        assertThat(limits.reasons()).containsExactly("width over 12 ft");
    }

    @Test
    void aTallLoadMovesInDaylightToo() {
        assertThat(Windows.limitsFor(load("10'", "15'")).daylightOnly()).isTrue();
    }

    @Test
    void aSuperloadLosesTheWeekendAsWellAsTheNight() {
        TravelLimits limits = Windows.limitsFor(load(), true, false);
        assertThat(limits.daylightOnly()).isTrue();
        assertThat(limits.noWeekend()).isTrue();
        assertThat(limits).hasToString("daylight only, no weekend travel");
    }

    @Test
    void aHighLoadCarriesAWindHoldThatIsNotATimeRule() {
        TravelLimits limits = Windows.limitsFor(load(), false, true);
        assertThat(limits.windHoldMph()).contains(30);
        assertThat(limits.daylightOnly()).isFalse();
        assertThat(limits.toString()).contains("hold above 30 mph wind");
    }

    @Test
    void aBroadLoadIsASailAndParksInALighterWind() {
        TravelLimits limits = Windows.limitsFor(wide("14'6"));
        assertThat(limits.windHoldMph()).contains(25);
        assertThat(limits.daylightOnly()).isTrue();
        assertThat(limits.reasons()).containsExactly("width over 12 ft");
        assertThat(limits).hasToString("daylight only, hold above 25 mph wind");
        assertThat(Windows.limitsFor(wide("14'")).windHoldMph()).isEmpty();
    }

    @Test
    void aTallLoadIsASailToo() {
        assertThat(Windows.limitsFor(load("10'", "15'1")).windHoldMph()).contains(25);
        assertThat(Windows.limitsFor(load("10'", "15'")).windHoldMph()).isEmpty();
    }

    @Test
    void aHighCentreOnASailParksAtTheLighterOfTheTwo() {
        TravelLimits limits = Windows.limitsFor(load("15'", "13'6"), false, true);
        assertThat(limits.windHoldMph()).contains(Windows.SAIL_HOLD_MPH);
        assertThat(Windows.limitsFor(load(), false, true).windHoldMph()).contains(Windows.WIND_HOLD_MPH);
    }

    @Test
    void theWhenTableIsItsLinesInPrintingOrder() {
        assertThat(Windows.RULES)
                .extracting(WhenRule::reason)
                .containsExactly("width over 12 ft", "height over 14 ft 6 in", "width over 14 ft", "height over 15 ft");
        assertThat(Windows.RULES)
                .extracting(WhenRule::hold)
                .containsExactly(
                        WhenRule.Hold.DAYLIGHT, WhenRule.Hold.DAYLIGHT, WhenRule.Hold.WIND, WhenRule.Hold.WIND);
    }

    @Test
    void aWhenLineThatContradictsItselfIsRefused() {
        assertThatThrownBy(() -> new WhenRule("width", Inches.of(12), WhenRule.Hold.DAYLIGHT, 25))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new WhenRule("width", Inches.of(12), WhenRule.Hold.WIND, 0))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> WhenRule.daylight("weight", Inches.of(12))).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> WhenRule.daylight("width", Inches.ZERO)).isInstanceOf(Impossible.class);
    }

    @Test
    void aLoadWithNoRulesMayRunThroughTheNight() {
        TravelDay day = Windows.travelDay(JUNE, Windows.limitsFor(load()), YEAR);
        assertThat(day.open()).isTrue();
        assertThat(day.opens()).contains(LocalTime.of(0, 0));
        assertThat(day.closes()).contains(LocalTime.of(23, 59));
    }

    @Test
    void aDaylightLoadGetsTheWindowOutOfTheDaylightTable() {
        TravelDay day = Windows.travelDay(JUNE, Windows.limitsFor(wide("13'")), YEAR);
        assertThat(day.opens()).contains(LocalTime.of(4, 31));
        assertThat(day.closes()).contains(LocalTime.of(20, 55));
        assertThat(day).hasToString("2025-06-16 04:31 to 20:55");
    }

    @Test
    void aSuperloadIsShutOutOfSaturdayAndSunday() {
        TravelLimits limits = Windows.limitsFor(load(), true, false);
        assertThat(Windows.travelDay(LocalDate.of(2025, 6, 21), limits, YEAR).open())
                .isFalse();
        assertThat(Windows.travelDay(LocalDate.of(2025, 6, 20), limits, YEAR).open())
                .isTrue();
        assertThat(Windows.travelDay(LocalDate.of(2025, 6, 21), limits, YEAR).reason())
                .isEqualTo("no weekend travel");
    }

    @Test
    void aHolidayBanClosesTheDayAndNamesTheHoliday() {
        TravelDay day = Windows.travelDay(LocalDate.of(2025, 7, 4), Windows.limitsFor(load()), YEAR);
        assertThat(day.open()).isFalse();
        assertThat(day.reason()).isEqualTo("independence day travel ban");
        assertThat(day.minutes()).isEqualTo(0);
    }

    @Test
    void theMorningBeforeABanStartsIsStillAWorkingMorning() {
        TravelDay day = Windows.travelDay(LocalDate.of(2025, 7, 3), Windows.limitsFor(load()), YEAR);
        assertThat(day.open()).isTrue();
        assertThat(day.opens()).contains(LocalTime.of(0, 0));
        assertThat(day.closes()).contains(LocalTime.of(12, 0));
    }

    @Test
    void theMorningAfterABanLiftsOpensLate() {
        TravelDay day = Windows.travelDay(LocalDate.of(2025, 9, 1), Windows.limitsFor(load()), YEAR);
        assertThat(day.open()).isFalse();
        day = Windows.travelDay(LocalDate.of(2025, 8, 29), Windows.limitsFor(load()), YEAR);
        assertThat(day.closes()).contains(LocalTime.of(12, 0));
    }

    @Test
    void aCurfewTakesABiteAndTheLongerPieceIsKept() {
        Curfew curfew = new Curfew(WEEKDAYS, LocalTime.of(6, 30), LocalTime.of(8, 30));
        TravelDay day = Windows.travelDay(JUNE, Windows.limitsFor(load()), YEAR, curfew);
        assertThat(day.opens()).contains(LocalTime.of(8, 30));
        assertThat(day.closes()).contains(LocalTime.of(23, 59));
    }

    @Test
    void aCurfewLateInTheDayLeavesTheMorning() {
        Curfew curfew = new Curfew(WEEKDAYS, LocalTime.of(14, 0), LocalTime.of(23, 59));
        TravelDay day = Windows.travelDay(JUNE, Windows.limitsFor(load()), YEAR, curfew);
        assertThat(day.opens()).contains(LocalTime.of(0, 0));
        assertThat(day.closes()).contains(LocalTime.of(14, 0));
    }

    @Test
    void aCurfewOverTheWholeWindowClosesTheDay() {
        Curfew curfew = new Curfew(WEEKDAYS, LocalTime.of(0, 0), LocalTime.of(23, 59));
        TravelDay day = Windows.travelDay(JUNE, Windows.limitsFor(load()), YEAR, curfew);
        assertThat(day.open()).isFalse();
        assertThat(day.reason()).isEqualTo("the curfew covers the whole window");
    }

    @Test
    void aCurfewOnDaysThisLoadDoesNotTravelIsIgnored() {
        Curfew curfew = new Curfew(Set.of(SATURDAY, SUNDAY), LocalTime.of(0, 0), LocalTime.of(23, 59));
        assertThat(Windows.travelDay(JUNE, Windows.limitsFor(load()), YEAR, curfew)
                        .open())
                .isTrue();
    }

    @Test
    void aPermitSpanComesBackDayByDayOpenOrShut() {
        List<TravelDay> days = Windows.travelDays(
                LocalDate.of(2025, 6, 30), LocalDate.of(2025, 7, 6), Windows.limitsFor(load()), YEAR);
        assertThat(days).hasSize(7);
        assertThat(Windows.openDays(days)).isEqualTo(6);
        assertThat(days.stream().filter(one -> !one.open()).map(one -> one.day().getDayOfMonth()))
                .containsExactly(4);
    }

    @Test
    void aSpanThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> Windows.travelDays(
                        LocalDate.of(2025, 7, 6), LocalDate.of(2025, 6, 30), Windows.limitsFor(load()), YEAR))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aDaylightSuperloadOverAHolidayWeekLosesMostOfIt() {
        TravelLimits limits = Windows.limitsFor(wide("16'"), true, false);
        List<TravelDay> days =
                Windows.travelDays(LocalDate.of(2025, 8, 28), LocalDate.of(2025, 9, 3), limits, YEAR, Optional.empty());
        assertThat(Windows.openDays(days)).isEqualTo(4);
        assertThat(days.stream().filter(TravelDay::open).map(one -> one.day().getDayOfWeek()))
                .containsExactly(THURSDAY, FRIDAY, TUESDAY, WEDNESDAY);
    }
}
