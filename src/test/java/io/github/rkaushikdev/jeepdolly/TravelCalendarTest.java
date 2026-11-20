package io.github.rkaushikdev.jeepdolly;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The office calendar and the road calendar, worked against 2025 and 2026. */
class TravelCalendarTest {

    @Test
    void theNthWeekdayLandsWhereAWallCalendarSays() {
        assertThat(TravelCalendar.nthWeekday(2025, 1, MONDAY, 3)).isEqualTo(LocalDate.of(2025, 1, 20));
        assertThat(TravelCalendar.nthWeekday(2025, 9, MONDAY, 1)).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(TravelCalendar.nthWeekday(2025, 11, THURSDAY, 4)).isEqualTo(LocalDate.of(2025, 11, 27));
    }

    @Test
    void askingForAWeekdayAMonthDoesNotHaveIsAnError() {
        assertThatThrownBy(() -> TravelCalendar.nthWeekday(2025, 2, MONDAY, 5)).isInstanceOf(Impossible.class);
    }

    @Test
    void theLastWeekdayOfAMonthWalksBackFromTheEnd() {
        assertThat(TravelCalendar.lastWeekday(2025, 5, MONDAY)).isEqualTo(LocalDate.of(2025, 5, 26));
        assertThat(TravelCalendar.lastWeekday(2025, 12, MONDAY)).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(TravelCalendar.lastWeekday(2024, 2, MONDAY)).isEqualTo(LocalDate.of(2024, 2, 26));
    }

    @Test
    void aFixedHolidayOnAWeekendMovesToTheNearWeekday() {
        assertThat(TravelCalendar.observed(LocalDate.of(2026, 7, 4))).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(TravelCalendar.observed(LocalDate.of(2027, 7, 4))).isEqualTo(LocalDate.of(2027, 7, 5));
        assertThat(TravelCalendar.observed(LocalDate.of(2025, 7, 4))).isEqualTo(LocalDate.of(2025, 7, 4));
    }

    @Test
    void theYearHoldsElevenHolidaysInTheOrderTheyFall() {
        List<Holiday> year = TravelCalendar.holidays(2025);
        assertThat(year).hasSize(11);
        assertThat(year).extracting(Holiday::day).isSorted();
        assertThat(year.get(0).name()).isEqualTo("new year");
        assertThat(year.get(year.size() - 1).name()).isEqualTo("christmas");
    }

    @Test
    void theHolidaysOf2025AreTheOnesOnTheWall() {
        Map<String, LocalDate> named =
                TravelCalendar.holidays(2025).stream().collect(Collectors.toMap(Holiday::name, Holiday::day));
        assertThat(named.get("new year")).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(named.get("martin luther king day")).isEqualTo(LocalDate.of(2025, 1, 20));
        assertThat(named.get("presidents day")).isEqualTo(LocalDate.of(2025, 2, 17));
        assertThat(named.get("patriots day")).isEqualTo(LocalDate.of(2025, 4, 21));
        assertThat(named.get("memorial day")).isEqualTo(LocalDate.of(2025, 5, 26));
        assertThat(named.get("independence day")).isEqualTo(LocalDate.of(2025, 7, 4));
        assertThat(named.get("labor day")).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(named.get("indigenous peoples day")).isEqualTo(LocalDate.of(2025, 10, 13));
        assertThat(named.get("veterans day")).isEqualTo(LocalDate.of(2025, 11, 11));
        assertThat(named.get("thanksgiving")).isEqualTo(LocalDate.of(2025, 11, 27));
        assertThat(named.get("christmas")).isEqualTo(LocalDate.of(2025, 12, 25));
    }

    @Test
    void aHolidayPrintsAsTheOfficeWritesItOnTheBoard() {
        Holiday memorial = TravelCalendar.holidays(2025).stream()
                .filter(one -> one.name().equals("memorial day"))
                .findFirst()
                .orElseThrow();
        assertThat(memorial).hasToString("memorial day 2025-05-26");
    }

    @Test
    void theCounterIsShutOnWeekendsAndHolidays() {
        PermitCalendar year = new PermitCalendar(2025);
        assertThat(year.isOpen(LocalDate.of(2025, 8, 6))).isTrue();
        assertThat(year.isOpen(LocalDate.of(2025, 8, 9))).isFalse();
        assertThat(year.isOpen(LocalDate.of(2025, 8, 10))).isFalse();
        assertThat(year.isOpen(LocalDate.of(2025, 5, 26))).isFalse();
        assertThat(year).hasToString("PermitCalendar(2025)");
    }

    @Test
    void theNextOpenDayStepsOverAHolidayWeekend() {
        PermitCalendar year = new PermitCalendar(2025);
        assertThat(year.nextOpen(LocalDate.of(2025, 5, 23))).isEqualTo(LocalDate.of(2025, 5, 27));
        assertThat(year.nextOpen(LocalDate.of(2025, 8, 6))).isEqualTo(LocalDate.of(2025, 8, 7));
        assertThat(year.nextOpen(LocalDate.of(2025, 11, 26))).isEqualTo(LocalDate.of(2025, 11, 28));
    }

    @Test
    void countingOpenDaysIncludesBothEndsOfTheSpan() {
        PermitCalendar year = new PermitCalendar(2025);
        assertThat(year.openDaysBetween(LocalDate.of(2025, 8, 4), LocalDate.of(2025, 8, 8)))
                .isEqualTo(5);
        assertThat(year.openDaysBetween(LocalDate.of(2025, 8, 4), LocalDate.of(2025, 8, 10)))
                .isEqualTo(5);
        assertThat(year.openDaysBetween(LocalDate.of(2025, 5, 23), LocalDate.of(2025, 5, 27)))
                .isEqualTo(2);
        assertThat(year.openDaysBetween(LocalDate.of(2025, 8, 4), LocalDate.of(2025, 8, 4)))
                .isEqualTo(1);
    }

    @Test
    void countingBackwardsIsAMistakeAndSaysSo() {
        assertThatThrownBy(() ->
                        new PermitCalendar(2025).openDaysBetween(LocalDate.of(2025, 8, 8), LocalDate.of(2025, 8, 4)))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void onlyTheLongWeekendsCloseTheRoad() {
        PermitCalendar year = new PermitCalendar(2025);
        assertThat(year.bans())
                .extracting(Window::reason)
                .containsExactly(
                        "memorial day travel ban",
                        "independence day travel ban",
                        "labor day travel ban",
                        "thanksgiving travel ban");
    }

    @Test
    void aMondayHolidayPullsTheWholeWeekendIntoTheBan() {
        PermitCalendar year = new PermitCalendar(2025);
        Window labor = year.bans().get(2);
        assertThat(labor.start()).isEqualTo(LocalDateTime.of(2025, 8, 29, 12, 0));
        assertThat(labor.end()).isEqualTo(LocalDateTime.of(2025, 9, 1, 23, 59));
        assertThat(year.banned(LocalDateTime.of(2025, 8, 29, 11, 59))).isFalse();
        assertThat(year.banned(LocalDateTime.of(2025, 8, 29, 12, 0))).isTrue();
        assertThat(year.banned(LocalDateTime.of(2025, 8, 30, 8, 0))).isTrue();
        assertThat(year.banned(LocalDateTime.of(2025, 9, 2, 0, 1))).isFalse();
    }

    @Test
    void thanksgivingRunsThroughTheFridayAfterIt() {
        PermitCalendar year = new PermitCalendar(2025);
        assertThat(year.banned(LocalDateTime.of(2025, 11, 26, 12, 0))).isTrue();
        assertThat(year.banned(LocalDateTime.of(2025, 11, 28, 15, 0))).isTrue();
        assertThat(year.banned(LocalDateTime.of(2025, 11, 29, 6, 0))).isFalse();
    }

    @Test
    void aHolidayInTheMiddleOfAWeekBansOnlyItsOwnAfternoonBefore() {
        PermitCalendar year = new PermitCalendar(2026);
        assertThat(year.holidayOn(LocalDate.of(2026, 12, 25))).isPresent();
        assertThat(year.banAt(LocalDateTime.of(2026, 12, 25, 9, 0))).isEmpty();
    }

    @Test
    void theBanCoveringAMomentNamesItself() {
        PermitCalendar year = new PermitCalendar(2025);
        Window window = year.banAt(LocalDateTime.of(2025, 7, 4, 9, 0)).orElseThrow();
        assertThat(window.reason()).isEqualTo("independence day travel ban");
        assertThat(window.toString()).startsWith("independence day travel ban 2025-07-03 12:00");
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsNotAWindow() {
        assertThatThrownBy(() -> new Window(
                        LocalDateTime.of(2025, 7, 4, 12, 0), LocalDateTime.of(2025, 7, 3, 12, 0), "backwards"))
                .isInstanceOf(Impossible.class);
    }
}
