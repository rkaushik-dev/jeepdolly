package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import java.time.LocalDate;

/**
 * The office numbering, which is a counter and the rules around it.
 *
 * <p>Permit numbers have to be gapless and have to be unique. The counter hands them out one at a
 * time; this turns a sequence into a whole number for a day and a series, and knows that a reissued
 * permit takes a new number in the replacement series.
 */
public final class Numbering {

    private final Sequences counter;

    /** Numbering off a fresh in memory counter. */
    public Numbering() {
        this(new Counter());
    }

    /** Numbering off any counter, such as the locked row inside a database transaction. */
    public Numbering(Sequences counter) {
        this.counter = counter;
    }

    /** The counter the numbers come off. */
    public Sequences counter() {
        return counter;
    }

    /** The next single trip permit number for a day. */
    public PermitNumber nextNumber(LocalDate on) {
        return nextNumber(on, Series.SINGLE);
    }

    /** The next number in a series for a day, numbered in the day's year. */
    public PermitNumber nextNumber(LocalDate on, Series series) {
        return new PermitNumber(on.getYear(), series, counter.take(on.getYear(), series));
    }

    /**
     * A reissued permit takes a new number in the replacement series.
     *
     * @throws Impossible when the number being replaced is already a replacement
     */
    public PermitNumber replacementFor(PermitNumber number, LocalDate on) {
        if (number.series() == Series.REPLACEMENT) {
            throw new Impossible(number + " is already a replacement");
        }
        return nextNumber(on, Series.REPLACEMENT);
    }

    /** How many single trip permits were numbered in a year. */
    public int issuedIn(int year) {
        return issuedIn(year, Series.SINGLE);
    }

    /** How many numbers in a series were handed out in a year. */
    public int issuedIn(int year, Series series) {
        return counter.issued(year, series);
    }
}
