package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * What an ordinary pilot car card does not cover: working a height pole, and working with
 * the state police on a superload. Each is examined for separately and carried on the card.
 */
public enum Endorsement implements Valued {
    HEIGHT_POLE("height pole"),
    POLICE_LIAISON("police liaison");

    private final String value;

    Endorsement(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static Endorsement of(String value) {
        return Valued.of(Endorsement.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
