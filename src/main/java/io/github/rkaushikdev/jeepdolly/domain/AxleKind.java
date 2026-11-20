package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Where on the combination an axle sits.
 */
public enum AxleKind implements Valued {
    STEER("steer"),
    DRIVE("drive"),
    TRAILER("trailer"),
    JEEP("jeep"),
    BOOSTER("booster");

    private final String value;

    AxleKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static AxleKind of(String value) {
        return Valued.of(AxleKind.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
