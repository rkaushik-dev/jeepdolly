package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Valued;

/** What was wrong at the roadside, in the words the citation prints. */
public enum Offence implements Valued {
    OVERWEIGHT("overweight"),
    NO_PERMIT("no permit"),
    OFF_ROUTE("off route"),
    IN_CURFEW("moved in a curfew"),
    NO_ESCORT("escorts not present"),
    NO_MARKING("banners or flags missing"),
    AXLES_NOT_AS_DECLARED("axle spacing not as declared"),
    EXPIRED_PERMIT("permit expired");

    private final String value;

    Offence(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The offence printed as {@code value}. */
    public static Offence of(String value) {
        return Valued.of(Offence.class, value);
    }

    /** Whether the amount is read off a scale or is a flat sum. */
    public boolean byWeight() {
        return this == OVERWEIGHT;
    }

    @Override
    public String toString() {
        return value;
    }
}
