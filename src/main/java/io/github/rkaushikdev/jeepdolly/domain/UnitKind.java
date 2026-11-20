package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * A piece of equipment, by what it does in the string.
 */
public enum UnitKind implements Valued {
    TRACTOR("tractor"),
    TRUCK("truck"),
    LOWBOY("lowboy"),
    RGN("removable gooseneck"),
    FLATBED("flatbed"),
    STEP("step deck"),
    JEEP("jeep dolly"),
    BOOSTER("booster");

    private final String value;

    UnitKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static UnitKind of(String value) {
        return Valued.of(UnitKind.class, value);
    }

    /** Whether this unit pulls the string. */
    public boolean powered() {
        return this == TRACTOR || this == TRUCK;
    }

    /** Whether the load goes on this unit. */
    public boolean carriesLoad() {
        return this == LOWBOY || this == RGN || this == FLATBED || this == STEP;
    }

    @Override
    public String toString() {
        return value;
    }
}
