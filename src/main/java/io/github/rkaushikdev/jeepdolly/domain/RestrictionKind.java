package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * What sort of trouble a restriction is.
 *
 * <p>A clearance is a height under a bridge, a gantry or a set of wires. A width restriction is a
 * narrow structure. A weight restriction is a posted structure. A seasonal restriction is a road
 * that goes soft in the spring and carries the region the thaw order names. A curfew is a time of
 * day on days of the week. A closure is a road that is not there this month, and no load of any
 * size gets past it.
 */
public enum RestrictionKind implements Valued {
    CLEARANCE("clearance"),
    WIDTH("width"),
    WEIGHT("weight"),
    SEASONAL("seasonal"),
    CURFEW("curfew"),
    CLOSURE("closure");

    private final String value;

    RestrictionKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static RestrictionKind of(String value) {
        return Valued.of(RestrictionKind.class, value);
    }

    public boolean stopsEverything() {
        return this == CLOSURE;
    }

    @Override
    public String toString() {
        return value;
    }
}
