package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Why a duplicate copy was asked for, in the words the counter uses.
 *
 * <p>Four reasons cover everything anybody has ever said at the counter, and the fifth is other,
 * which wants a sentence. The reason decides nothing. It is read once a year, when somebody asks
 * whether the office should be emailing these instead.
 */
public enum Reason implements Valued {
    LOST("lost"),
    DAMAGED("damaged"),
    LEFT_BEHIND("left in another unit"),
    SECOND_DRIVER("second driver"),
    OTHER("other");

    private final String value;

    Reason(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The reason stored under {@code value}. */
    public static Reason of(String value) {
        return Valued.of(Reason.class, value);
    }

    /** Whether a copy asked for on this ground has to say what the ground was. */
    public boolean wantsWords() {
        return this == OTHER;
    }

    @Override
    public String toString() {
        return value;
    }
}
