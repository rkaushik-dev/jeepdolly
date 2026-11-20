package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Which document a number is for, by the one letter it carries.
 *
 * <p>Numbers sort by the letter, not by the order the series are listed in, which the ledger needs;
 * {@link PermitNumber} compares them by {@link #value()}.
 */
public enum Series implements Valued {
    SINGLE("P"),
    ANNUAL("A"),
    SUPERLOAD("S"),
    REPLACEMENT("R"),
    /**
     * Not a permit at all. An application reference, off the same counter so that a clerk reading
     * one out cannot confuse it with a permit number.
     */
    FILE("F"),
    /**
     * Not a permit either. A citation off the roadside, on the same counter for the same reason:
     * one number space, and nothing in it means two things.
     */
    CITATION("E");

    private final String value;

    Series(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The series lettered {@code value}. */
    public static Series of(String value) {
        return Valued.of(Series.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
