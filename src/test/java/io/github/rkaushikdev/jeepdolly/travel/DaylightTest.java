package io.github.rkaushikdev.jeepdolly.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Sunrise, sunset, and the half hour either side the permit allows. */
class DaylightTest {

    @Test
    void theMiddleOfAMonthComesStraightOffTheTable() {
        Daylight winter = Daylight.on(LocalDate.of(2025, 1, 15));
        assertThat(winter.sunrise()).isEqualTo(LocalTime.of(7, 12));
        assertThat(winter.sunset()).isEqualTo(LocalTime.of(16, 25));
    }

    @Test
    void thePermitDayReachesHalfAnHourPastEachEnd() {
        Daylight winter = Daylight.on(LocalDate.of(2025, 1, 15));
        assertThat(winter.opens()).isEqualTo(LocalTime.of(6, 42));
        assertThat(winter.closes()).isEqualTo(LocalTime.of(16, 55));
        assertThat(winter).hasToString("2025-01-15 06:42 to 16:55");
    }

    @Test
    void aDayBetweenTwoAnchorsIsWalkedBetweenThem() {
        LocalTime first = Daylight.on(LocalDate.of(2025, 1, 15)).sunrise();
        LocalTime last = Daylight.on(LocalDate.of(2025, 2, 15)).sunrise();
        LocalTime middle = Daylight.on(LocalDate.of(2025, 1, 31)).sunrise();
        assertThat(last).isBefore(middle);
        assertThat(middle).isBefore(first);
    }

    @Test
    void theClockGoesForwardInMarchAndTheTableFollows() {
        assertThat(Daylight.savingTime(LocalDate.of(2025, 3, 8))).isFalse();
        assertThat(Daylight.savingTime(LocalDate.of(2025, 3, 9))).isTrue();
        assertThat(Daylight.savingTime(LocalDate.of(2025, 11, 1))).isTrue();
        assertThat(Daylight.savingTime(LocalDate.of(2025, 11, 2))).isFalse();
    }

    @Test
    void theHourShowsUpOnBothEndsOfTheDay() {
        Daylight before = Daylight.on(LocalDate.of(2025, 3, 8));
        Daylight after = Daylight.on(LocalDate.of(2025, 3, 9));
        assertThat(after.sunrise().getHour()).isEqualTo(before.sunrise().getHour() + 1);
        assertThat(after.sunset().getHour()).isEqualTo(before.sunset().getHour() + 1);
    }

    @Test
    void aSummerDayIsLongAndAWinterDayIsNot() {
        assertThat(Daylight.on(LocalDate.of(2025, 6, 15)).hours()).isEqualTo(16);
        assertThat(Daylight.on(LocalDate.of(2025, 12, 15)).hours()).isEqualTo(9);
    }

    @Test
    void aMomentIsInsideTheWindowOrItIsNot() {
        assertThat(Daylight.isDaylight(LocalDate.of(2025, 6, 15), LocalTime.of(6, 0)))
                .isTrue();
        assertThat(Daylight.isDaylight(LocalDate.of(2025, 12, 15), LocalTime.of(6, 0)))
                .isFalse();
        assertThat(Daylight.isDaylight(LocalDate.of(2025, 6, 15), LocalTime.of(21, 0)))
                .isFalse();
    }

    @Test
    void theWindowIsClosedAtBothEndsAndTheEdgesAreInsideIt() {
        Daylight day = Daylight.on(LocalDate.of(2025, 6, 15));
        assertThat(day.covers(day.opens())).isTrue();
        assertThat(day.covers(day.closes())).isTrue();
    }

    @Test
    void theWorstDayOfTheYearIsAroundTheSolstice() {
        Daylight worst = Daylight.shortestDay(2025);
        assertThat(worst.day().getMonthValue()).isEqualTo(12);
        assertThat(worst.day().getDayOfMonth()).isBetween(10, 31);
        assertThat(worst.hours()).isEqualTo(9);
    }

    @Test
    void aDayBeforeTheTableStartsIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> Daylight.on(LocalDate.of(1800, 6, 15))).isInstanceOf(Impossible.class);
    }

    @Test
    void theYearBoundaryWalksBetweenDecemberAndJanuary() {
        LocalTime december = Daylight.on(LocalDate.of(2025, 12, 28)).sunrise();
        LocalTime january = Daylight.on(LocalDate.of(2026, 1, 5)).sunrise();
        assertThat(december).isBefore(january);
    }
}
