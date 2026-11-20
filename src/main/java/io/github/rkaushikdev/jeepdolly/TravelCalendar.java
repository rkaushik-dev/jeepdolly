package io.github.rkaushikdev.jeepdolly;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The calendar a permit is written against: the office's holidays and the road's travel bans.
 *
 * <p>Two different calendars live here and they are not the same thing. The office calendar says
 * which days the counter is open, which is what an application date and a processing clock run on.
 * The road calendar says when an oversize load is not allowed to move at all. The long weekends are
 * the ones that matter: the order the office works to bans movement from noon the day before a
 * holiday weekend until the end of the holiday itself, and a hauler who is caught out by that on a
 * Friday afternoon sits in a gravel pit until Tuesday.
 *
 * <p>Holidays are computed, not listed. A table of dates is a table somebody has to remember to
 * extend, and the year it is not extended is the year a permit gets written for Thanksgiving Day.
 *
 * <p>Nothing here carries a timezone. The state is one timezone wide and a permit that says noon
 * means noon where the load is.
 */
public final class TravelCalendar {

    /** Holidays whose ban window covers the whole weekend around them. */
    public static final Set<String> LONG_WEEKENDS =
            Set.of("memorial day", "independence day", "labor day", "thanksgiving");

    static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private TravelCalendar() {}

    /**
     * The nth given weekday of a month, one based.
     *
     * @throws Impossible when the month has no such weekday
     */
    public static LocalDate nthWeekday(int year, int month, DayOfWeek weekday, int n) {
        LocalDate first = LocalDate.of(year, month, 1);
        int ahead = Math.floorMod(weekday.getValue() - first.getDayOfWeek().getValue(), 7);
        LocalDate day = first.plusDays(ahead + 7L * (n - 1));
        if (day.getMonthValue() != month) {
            throw new Impossible(String.format(
                    Locale.ROOT, "no %d of weekday %d in %d-%02d", n, weekday.getValue() - 1, year, month));
        }
        return day;
    }

    /** The last given weekday of a month. */
    public static LocalDate lastWeekday(int year, int month, DayOfWeek weekday) {
        LocalDate last = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        int back = Math.floorMod(last.getDayOfWeek().getValue() - weekday.getValue(), 7);
        return last.minusDays(back);
    }

    /** A fixed date holiday falling on a weekend is kept on the near weekday. */
    public static LocalDate observed(LocalDate day) {
        return switch (day.getDayOfWeek()) {
            case SATURDAY -> day.minusDays(1);
            case SUNDAY -> day.plusDays(1);
            default -> day;
        };
    }

    /** Every holiday the office keeps in a year, in the order they fall. */
    public static List<Holiday> holidays(int year) {
        List<Holiday> found = new ArrayList<>(List.of(
                new Holiday(observed(LocalDate.of(year, 1, 1)), "new year", true),
                new Holiday(nthWeekday(year, 1, DayOfWeek.MONDAY, 3), "martin luther king day"),
                new Holiday(nthWeekday(year, 2, DayOfWeek.MONDAY, 3), "presidents day"),
                new Holiday(nthWeekday(year, 4, DayOfWeek.MONDAY, 3), "patriots day"),
                new Holiday(lastWeekday(year, 5, DayOfWeek.MONDAY), "memorial day"),
                new Holiday(observed(LocalDate.of(year, 7, 4)), "independence day", true),
                new Holiday(nthWeekday(year, 9, DayOfWeek.MONDAY, 1), "labor day"),
                new Holiday(nthWeekday(year, 10, DayOfWeek.MONDAY, 2), "indigenous peoples day"),
                new Holiday(observed(LocalDate.of(year, 11, 11)), "veterans day", true),
                new Holiday(nthWeekday(year, 11, DayOfWeek.THURSDAY, 4), "thanksgiving"),
                new Holiday(observed(LocalDate.of(year, 12, 25)), "christmas", true)));
        found.sort(null);
        return List.copyOf(found);
    }

    /**
     * Noon the day before through the end of the holiday, weekend included.
     *
     * <p>A holiday on a Monday pulls the whole weekend in, because the ban starts at noon on the
     * Friday. Thanksgiving runs through the Friday after it, which is the busiest travel day of the
     * year on every road in the state.
     */
    static Window banFor(Holiday holiday) {
        LocalDate startDay = holiday.day().minusDays(1);
        if (holiday.day().getDayOfWeek() == DayOfWeek.MONDAY) {
            startDay = holiday.day().minusDays(3);
        }
        LocalDate endDay = holiday.day();
        if (holiday.name().equals("thanksgiving")) {
            endDay = holiday.day().plusDays(1);
        }
        return new Window(
                LocalDateTime.of(startDay, LocalTime.of(12, 0)),
                LocalDateTime.of(endDay, LocalTime.of(23, 59)),
                holiday.name() + " travel ban");
    }
}
