package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * The broad kind of thing on the trailer, which is how the fee schedule groups commodities.
 */
public enum Family implements Valued {
    MACHINERY("machinery"),
    STRUCTURAL("structural"),
    VESSEL("vessel"),
    BUILDING("building"),
    FOREST("forest"),
    AGRICULTURAL("agricultural"),
    BULK("bulk"),
    UTILITY("utility");

    private final String value;

    Family(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static Family of(String value) {
        return Valued.of(Family.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
