package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Whether a pilot car operator can be named on a permit for a day.
 *
 * <p>An operator's own suspension is read first, then the company's, then the card's expiry,
 * because a company suspension reaches every operator on its books whatever their own card says.
 */
public enum Standing implements Valued {
    GOOD("good"),
    EXPIRED("expired"),
    SUSPENDED("suspended"),
    COMPANY_SUSPENDED("company suspended");

    private final String value;

    Standing(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static Standing of(String value) {
        return Valued.of(Standing.class, value);
    }

    /** Whether the operator may be named. */
    public boolean available() {
        return this == GOOD;
    }

    @Override
    public String toString() {
        return value;
    }
}
