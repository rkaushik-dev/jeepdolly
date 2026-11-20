package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * How a carrier pays for a permit: at the counter, on account, or out of escrow.
 */
public enum Billing implements Valued {
    COUNTER("counter"),
    ACCOUNT("account"),
    ESCROW("escrow");

    private final String value;

    Billing(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static Billing of(String value) {
        return Valued.of(Billing.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
