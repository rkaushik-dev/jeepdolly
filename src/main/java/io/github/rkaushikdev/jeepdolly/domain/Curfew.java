package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Days of the week and a time of day, as a curfew order is written: rush hour through a village,
 * school buses on a route with no shoulder.
 */
public record Curfew(Set<DayOfWeek> days, LocalTime start, LocalTime end) {

    private static final String LETTERS = "MTWTFSS";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    public Curfew {
        if (days.isEmpty()) {
            throw new Impossible("a curfew names the days it runs on");
        }
        days = Set.copyOf(days);
        if (!start.isBefore(end)) {
            throw new Impossible("a curfew ends after it starts");
        }
    }

    /** Whether the order runs on that day at that time, both ends of the clock included. */
    public boolean covers(LocalDate when, LocalTime clock) {
        return days.contains(when.getDayOfWeek()) && !clock.isBefore(start) && !clock.isAfter(end);
    }

    /** As the order prints: {@code MTWTF 06:30 to 08:30}. */
    @Override
    public String toString() {
        String names = days.stream()
                .sorted()
                .map(day -> String.valueOf(LETTERS.charAt(day.getValue() - 1)))
                .collect(Collectors.joining());
        return names + " " + CLOCK.format(start) + " to " + CLOCK.format(end);
    }
}
