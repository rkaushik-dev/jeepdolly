package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Dollars;
import java.time.LocalDate;

/**
 * One unpaid citation, with the day it stops being merely late.
 *
 * @param due the last day it is merely late; after it, the citation holds the carrier
 * @param contested whether the carrier has disputed it, which means it never holds
 */
public record Owed(String number, LocalDate written, Dollars amount, LocalDate due, boolean contested) {

    /** An unpaid citation nobody has disputed. */
    public Owed(String number, LocalDate written, Dollars amount, LocalDate due) {
        this(number, written, amount, due, false);
    }

    /** Whether this one holds the carrier on that day. */
    public boolean pastDueOn(LocalDate day) {
        return !contested && day.isAfter(due);
    }

    @Override
    public String toString() {
        String mark = contested ? " (contested)" : "";
        return number + "  " + amount + "  due " + due + mark;
    }
}
