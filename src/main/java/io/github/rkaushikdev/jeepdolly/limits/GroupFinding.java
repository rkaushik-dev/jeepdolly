package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleGroup;

/**
 * One group of the stream against the statutory table.
 *
 * @param position where the group stands in the stream, counted from zero
 */
public record GroupFinding(int position, AxleGroup group, Pounds limit) {

    public Pounds weight() {
        return group.weight();
    }

    /** How far past the limit the group is, never below zero. */
    public Pounds over() {
        return weight().over(limit);
    }

    public boolean passes() {
        return over().value() == 0;
    }

    /** As a line of the letter: {@code group 3, tridem 48,000 lb, allowed 44,000 lb, over by 4,000 lb}. */
    @Override
    public String toString() {
        String verdict = passes() ? "within" : "over by " + over();
        return "group " + (position + 1) + ", " + group.name() + " " + weight() + ", allowed " + limit + ", " + verdict;
    }
}
