package io.github.rkaushikdev.jeepdolly;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One year of the office calendar and one year of the road calendar. */
public final class PermitCalendar {

    private final int year;
    private final List<Holiday> holidays;
    private final Map<LocalDate, Holiday> byDay = new LinkedHashMap<>();
    private final List<Window> bans;

    public PermitCalendar(int year) {
        this.year = year;
        this.holidays = TravelCalendar.holidays(year);
        holidays.forEach(holiday -> byDay.put(holiday.day(), holiday));
        this.bans = holidays.stream()
                .filter(holiday -> TravelCalendar.LONG_WEEKENDS.contains(holiday.name()))
                .map(TravelCalendar::banFor)
                .toList();
    }

    public int year() {
        return year;
    }

    /** The holidays of the year, in the order they fall. */
    public List<Holiday> holidays() {
        return holidays;
    }

    /** The travel bans of the year, in the order they fall. */
    public List<Window> bans() {
        return bans;
    }

    public Optional<Holiday> holidayOn(LocalDate day) {
        return Optional.ofNullable(byDay.get(day));
    }

    public boolean isHoliday(LocalDate day) {
        return byDay.containsKey(day);
    }

    /** The counter is open on weekdays that are not holidays. */
    public boolean isOpen(LocalDate day) {
        DayOfWeek weekday = day.getDayOfWeek();
        return weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY && !isHoliday(day);
    }

    /** The next day the counter is open, not counting the day given. */
    public LocalDate nextOpen(LocalDate day) {
        LocalDate following = day.plusDays(1);
        while (!isOpen(following)) {
            following = following.plusDays(1);
        }
        return following;
    }

    /**
     * Open days from {@code start} up to and including {@code end}.
     *
     * @throws Impossible when the span ends before it starts
     */
    public int openDaysBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new Impossible("the span ends before it starts");
        }
        int count = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (isOpen(day)) {
                count++;
            }
        }
        return count;
    }

    /** The travel ban covering this moment, if there is one. */
    public Optional<Window> banAt(LocalDateTime when) {
        return bans.stream().filter(window -> window.covers(when)).findFirst();
    }

    public boolean banned(LocalDateTime when) {
        return banAt(when).isPresent();
    }

    @Override
    public String toString() {
        return "PermitCalendar(" + year + ")";
    }
}
