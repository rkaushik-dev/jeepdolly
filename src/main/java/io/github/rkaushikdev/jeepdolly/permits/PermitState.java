package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * What the permit document is.
 *
 * <p>Issued is live. Void is a decision: somebody cancelled it, or the carrier lost the load, or an
 * inspector found a lie on it, and it is kept with the reason. Superseded means an amendment
 * replaced it; it is kept because the trip may already have started under it. Expired is not a
 * state at all: it is a fact about the calendar, so it is computed and never stored.
 */
public enum PermitState implements Valued {
    ISSUED("issued"),
    VOID("void"),
    SUPERSEDED("superseded");

    private final String value;

    PermitState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The state stored under {@code value}. */
    public static PermitState of(String value) {
        return Valued.of(PermitState.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
