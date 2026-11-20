package io.github.rkaushikdev.jeepdolly;

import java.time.LocalDateTime;

/** A closed span of clock time with a reason attached, such as a holiday travel ban. */
public record Window(LocalDateTime start, LocalDateTime end, String reason) {

    public Window {
        if (!end.isAfter(start)) {
            throw new Impossible("a window ends after it starts");
        }
    }

    /** Whether the moment falls inside the window, both ends included. */
    public boolean covers(LocalDateTime when) {
        return !when.isBefore(start) && !when.isAfter(end);
    }

    @Override
    public String toString() {
        return reason + " " + TravelCalendar.MINUTE.format(start) + " to " + TravelCalendar.MINUTE.format(end);
    }
}
