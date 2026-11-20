package io.github.rkaushikdev.jeepdolly.travel;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.TravelCalendar;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/**
 * Sunrise, sunset, and the window a daylight only permit may travel in, for one day.
 *
 * <p>A great many permits say daylight hours only, and the office has to be able to say what that
 * means for a Tuesday in November without anybody looking it up. This is not an ephemeris and it
 * does not pretend to be. It holds sunrise and sunset at the middle of each month for the middle of
 * the state and walks a straight line between them. Against the published tables it is within
 * about four minutes across the year, which is inside the half hour the rule allows either side.
 *
 * <p>The permit day runs from half an hour before sunrise to half an hour after sunset: civil
 * twilight in round numbers, which is what the enforcement people work to. Daylight saving is
 * applied by the calendar and not by the sun. The table is standard time, and from the second
 * Sunday in March to the first Sunday in November an hour is added to both ends, because that is
 * what the clock in the cab says.
 *
 * @param opens half an hour before sunrise
 * @param closes half an hour after sunset
 */
public record Daylight(LocalDate day, LocalTime sunrise, LocalTime sunset, LocalTime opens, LocalTime closes) {

    /** The permit day reaches this far past each end. */
    public static final int TWILIGHT_MINUTES = 30;

    public static final int MINUTES_PER_DAY = 24 * 60;

    /** Sunrise and sunset in minutes after midnight, standard time, on the 15th of each month. */
    private static final Map<Integer, int[]> MID_MONTH = Map.ofEntries(
            Map.entry(1, new int[] {432, 985}),
            Map.entry(2, new int[] {400, 1027}),
            Map.entry(3, new int[] {352, 1067}),
            Map.entry(4, new int[] {298, 1107}),
            Map.entry(5, new int[] {258, 1144}),
            Map.entry(6, new int[] {241, 1166}),
            Map.entry(7, new int[] {257, 1160}),
            Map.entry(8, new int[] {290, 1121}),
            Map.entry(9, new int[] {324, 1067}),
            Map.entry(10, new int[] {359, 1015}),
            Map.entry(11, new int[] {399, 975}),
            Map.entry(12, new int[] {429, 965}));

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * Sunrise, sunset and the travel window for one day.
     *
     * @throws Impossible for a day before the table starts
     */
    public static Daylight on(LocalDate day) {
        if (day.getYear() < 1900) {
            throw new Impossible("the table does not reach back that far");
        }
        LocalDate before;
        LocalDate after;
        if (day.getDayOfMonth() >= 15) {
            before = anchor(day);
            after = anchor(day.plusMonths(1));
        } else {
            before = anchor(day.minusMonths(1));
            after = anchor(day);
        }
        int whole = (int) ChronoUnit.DAYS.between(before, after);
        int part = (int) ChronoUnit.DAYS.between(before, day);
        int[] first = MID_MONTH.get(before.getMonthValue());
        int[] second = MID_MONTH.get(after.getMonthValue());
        int shift = savingTime(day) ? 60 : 0;
        int rise = between(first[0], second[0], part, whole) + shift;
        int set = between(first[1], second[1], part, whole) + shift;
        return new Daylight(
                day, clock(rise), clock(set), clock(rise - TWILIGHT_MINUTES), clock(set + TWILIGHT_MINUTES));
    }

    /** Whether the clock is an hour ahead on this day. */
    public static boolean savingTime(LocalDate day) {
        LocalDate starts = TravelCalendar.nthWeekday(day.getYear(), 3, DayOfWeek.SUNDAY, 2);
        LocalDate ends = TravelCalendar.nthWeekday(day.getYear(), 11, DayOfWeek.SUNDAY, 1);
        return !day.isBefore(starts) && day.isBefore(ends);
    }

    /** Whether this moment is inside the travel window of its day. */
    public static boolean isDaylight(LocalDate when, LocalTime clock) {
        return on(when).covers(clock);
    }

    /** The worst day of the year for a daylight only permit, the first of them on a tie. */
    public static Daylight shortestDay(int year) {
        LocalDate day = LocalDate.of(year, 1, 1);
        Daylight worst = on(day);
        while (day.getYear() == year) {
            Daylight found = on(day);
            if (found.span() < worst.span()) {
                worst = found;
            }
            day = day.plusDays(1);
        }
        return worst;
    }

    private static LocalDate anchor(LocalDate inMonth) {
        return inMonth.withDayOfMonth(15);
    }

    private static int between(int first, int second, int part, int whole) {
        return first + Math.floorDiv((second - first) * part, whole);
    }

    private static int minutes(LocalTime clock) {
        return clock.getHour() * 60 + clock.getMinute();
    }

    private static LocalTime clock(int minutes) {
        int kept = Math.max(0, Math.min(MINUTES_PER_DAY - 1, minutes));
        return LocalTime.of(kept / 60, kept % 60);
    }

    private int span() {
        return minutes(closes) - minutes(opens);
    }

    /** Whole hours of travel window, for the planning report. */
    public int hours() {
        return Math.floorDiv(span(), 60);
    }

    /** Whether the clock falls inside the travel window, both edges included. */
    public boolean covers(LocalTime clock) {
        return !clock.isBefore(opens) && !clock.isAfter(closes);
    }

    /** The travel window: {@code 2025-01-15 06:42 to 16:55}. */
    @Override
    public String toString() {
        return day + " " + CLOCK.format(opens) + " to " + CLOCK.format(closes);
    }
}
