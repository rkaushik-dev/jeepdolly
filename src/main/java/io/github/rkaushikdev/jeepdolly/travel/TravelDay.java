package io.github.rkaushikdev.jeepdolly.travel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * One day of a permit, and what is left of it once every when rule has been applied.
 *
 * @param opens when the load may start moving, empty on a closed day
 * @param closes when the load must be parked, empty on a closed day
 * @param reason why the day is closed, empty on an open one
 */
public record TravelDay(LocalDate day, Optional<LocalTime> opens, Optional<LocalTime> closes, String reason) {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /** A day the load may move on, between two times. */
    public static TravelDay window(LocalDate day, LocalTime opens, LocalTime closes) {
        return new TravelDay(day, Optional.of(opens), Optional.of(closes), "");
    }

    /** A day the load may not move at all, and why. */
    public static TravelDay closed(LocalDate day, String reason) {
        return new TravelDay(day, Optional.empty(), Optional.empty(), reason);
    }

    /** Whether the load may move at all on this day. */
    public boolean open() {
        return opens.isPresent() && closes.isPresent();
    }

    /** How many minutes the window is open, nothing on a closed day. */
    public int minutes() {
        if (!open()) {
            return 0;
        }
        LocalTime start = opens.get();
        LocalTime finish = closes.get();
        return (finish.getHour() * 60 + finish.getMinute()) - (start.getHour() * 60 + start.getMinute());
    }

    /** {@code 2025-06-16 04:31 to 20:55}, or {@code 2025-07-04 closed: ...}. */
    @Override
    public String toString() {
        if (!open()) {
            return day + " closed: " + reason;
        }
        return day + " " + CLOCK.format(opens.get()) + " to " + CLOCK.format(closes.get());
    }
}
