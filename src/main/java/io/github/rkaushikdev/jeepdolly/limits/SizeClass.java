package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Which of the three nested boxes a load falls in.
 *
 * <p>Legal moves with no permit and is not this office's business at all. Routine is past legal and
 * inside what a clerk may issue over the counter without asking anybody. Review is past the routine
 * box, where a person has to look at it.
 */
public enum SizeClass implements Valued {
    LEGAL("legal"),
    ROUTINE("routine"),
    REVIEW("review");

    private final String value;

    SizeClass(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static SizeClass of(String value) {
        return Valued.of(SizeClass.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
