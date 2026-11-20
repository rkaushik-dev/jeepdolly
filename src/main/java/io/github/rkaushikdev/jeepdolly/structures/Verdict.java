package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * What a rating allows: crossing normally, crossing under conditions, or not crossing at all.
 *
 * <p>Below the refuse line there is no discretion and no condition that fixes it. The hauler goes
 * another way, or the load comes apart, or the state runs a full analysis, which is a month and a
 * fee and is not this office's decision.
 */
public enum Verdict implements Valued {
    CLEARS("clears"),
    CONDITIONAL("conditional"),
    REFUSED("refused");

    private final String value;

    Verdict(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static Verdict of(String value) {
        return Valued.of(Verdict.class, value);
    }

    /** Whether the load goes over the structure at all. */
    public boolean crosses() {
        return this != REFUSED;
    }

    @Override
    public String toString() {
        return value;
    }
}
