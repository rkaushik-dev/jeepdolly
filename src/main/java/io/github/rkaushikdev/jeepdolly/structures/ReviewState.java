package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Where an engineering study stands in the review queue.
 *
 * <p>Waiting means nobody has picked it up. With engineer means somebody has, and the name is on
 * it. Cleared and refused are the engineer's signature either way. Held means the engineer wants
 * something first, and the item goes back to waiting when the hauler sends it.
 */
public enum ReviewState implements Valued {
    WAITING("waiting"),
    WITH_ENGINEER("with engineer"),
    CLEARED("cleared"),
    REFUSED("refused"),
    HELD("held");

    private final String value;

    ReviewState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static ReviewState of(String value) {
        return Valued.of(ReviewState.class, value);
    }

    /** Whether the item is still in the queue, picked up or not. */
    public boolean open() {
        return this == WAITING || this == WITH_ENGINEER || this == HELD;
    }

    /** Whether an engineer has signed it one way or the other. */
    public boolean decided() {
        return this == CLEARED || this == REFUSED;
    }

    @Override
    public String toString() {
        return value;
    }
}
