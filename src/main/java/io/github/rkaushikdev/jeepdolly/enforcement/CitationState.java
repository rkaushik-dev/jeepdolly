package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Where a citation stands.
 *
 * <p>Written is on file and unpaid. Paid is the end of it. Contested is the carrier saying no, in
 * writing, inside the window, and from there it is upheld, which then wants paying, or dismissed,
 * which is the end of it. Void is the office's own mistake, killed by a supervisor.
 */
public enum CitationState implements Valued {
    WRITTEN("written"),
    CONTESTED("contested"),
    UPHELD("upheld"),
    DISMISSED("dismissed"),
    PAID("paid"),
    VOID("void");

    private final String value;

    CitationState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The state stored under {@code value}. */
    public static CitationState of(String value) {
        return Valued.of(CitationState.class, value);
    }

    /** Whether money is still expected on it. */
    public boolean owing() {
        return this == WRITTEN || this == CONTESTED || this == UPHELD;
    }

    /** Whether nothing more will happen to it. */
    public boolean closed() {
        return this == PAID || this == DISMISSED || this == VOID;
    }

    @Override
    public String toString() {
        return value;
    }
}
