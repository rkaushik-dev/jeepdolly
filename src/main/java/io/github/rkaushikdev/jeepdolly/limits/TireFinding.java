package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;

/**
 * One axle against the rate the table publishes for its rubber.
 *
 * @param position where the axle stands in the stream, counted from zero
 * @param limit what the axle's tires will carry at the table's rate
 */
public record TireFinding(int position, Pounds weight, Pounds limit) {

    /** How far past the tire limit the axle is loaded, never below zero. */
    public Pounds over() {
        return weight.over(limit);
    }

    public boolean passes() {
        return over().value() == 0;
    }

    /** As a line of the letter: {@code axle 2 carries 30,000 lb on tires rated 22,000 lb}. */
    @Override
    public String toString() {
        return "axle " + (position + 1) + " carries " + weight + " on tires rated " + limit;
    }
}
