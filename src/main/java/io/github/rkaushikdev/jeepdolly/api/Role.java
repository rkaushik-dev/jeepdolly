package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.Valued;
import java.util.List;

/**
 * What a key may do, as a ladder rather than a matrix.
 *
 * <p>The office is eleven people and a matrix would be eleven columns of yes, so each rung may do
 * everything the rung below it may. A reader may look at anything: enforcement people, the state
 * police, and a carrier looking at its own permits. A clerk may file and submit applications and
 * issue ordinary permits. An engineer may also clear or refuse a study. A supervisor may also void
 * a permit, suspend a carrier, and edit the road log.
 */
public enum Role implements Valued {
    READER("reader"),
    CLERK("clerk"),
    ENGINEER("engineer"),
    SUPERVISOR("supervisor");

    /** The rungs, bottom first. */
    public static final List<Role> LADDER = List.of(values());

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The role stored under {@code value}. */
    public static Role of(String value) {
        return Valued.of(Role.class, value);
    }

    /** How high up the ladder this rung is, the reader at nought. */
    public int rank() {
        return LADDER.indexOf(this);
    }

    /** Whether this rung is at or above {@code wanted}. */
    public boolean reaches(Role wanted) {
        return rank() >= wanted.rank();
    }

    @Override
    public String toString() {
        return value;
    }
}
