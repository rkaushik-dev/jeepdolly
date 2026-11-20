package io.github.rkaushikdev.jeepdolly.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A decision to stop writing permits for somebody, and why.
 *
 * <p>A suspension is a decision somebody made, with a reason and a date it lifts, and it beats
 * everything else: a suspended carrier with perfect paper gets nothing.
 *
 * @param until the last day it runs, or empty for further notice
 */
public record Suspension(String reason, LocalDate fromDay, Optional<LocalDate> until) {

    /** A suspension until further notice. */
    public Suspension(String reason, LocalDate fromDay) {
        this(reason, fromDay, Optional.empty());
    }

    public boolean inForce(LocalDate day) {
        if (day.isBefore(fromDay)) {
            return false;
        }
        return until.isEmpty() || !day.isAfter(until.get());
    }

    /** With the reason, because the letter needs it. */
    @Override
    public String toString() {
        String ends = until.map(LocalDate::toString).orElse("further notice");
        return "suspended " + fromDay + " to " + ends + ": " + reason;
    }
}
