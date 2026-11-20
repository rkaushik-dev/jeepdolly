package io.github.rkaushikdev.jeepdolly.fees;

import io.github.rkaushikdev.jeepdolly.Dollars;

/** One line of a quote: what it is for, and what it costs. */
public record FeeLine(String description, Dollars amount) {

    /** The way the receipt prints it: {@code ordinary permit  $40.00}. */
    @Override
    public String toString() {
        return description + "  " + amount;
    }
}
