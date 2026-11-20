package io.github.rkaushikdev.jeepdolly;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * One holiday in one year, on the day the office actually keeps it.
 *
 * @param fixed whether it falls on a fixed date, and so moves off a weekend to the near weekday
 */
public record Holiday(LocalDate day, String name, boolean fixed) implements Comparable<Holiday> {

    private static final Comparator<Holiday> ORDER =
            Comparator.comparing(Holiday::day).thenComparing(Holiday::name).thenComparing(Holiday::fixed);

    /** A holiday that falls on a weekday of the month rather than a fixed date. */
    public Holiday(LocalDate day, String name) {
        this(day, name, false);
    }

    @Override
    public int compareTo(Holiday other) {
        return ORDER.compare(this, other);
    }

    /** As the office writes it on the board: {@code memorial day 2025-05-26}. */
    @Override
    public String toString() {
        return name + " " + day;
    }
}
