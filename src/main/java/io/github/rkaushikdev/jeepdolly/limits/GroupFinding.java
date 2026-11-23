package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleGroup;
import java.util.Comparator;

/**
 * One group of the stream against the statutory table.
 *
 * <p>The group limit assumes the suspension shares the load between the axles of the group, which
 * is what an equalising beam or an air ride with a common line does. A group whose heaviest axle
 * carries more than {@link #EQUALISED_PERCENT} percent over its lightest is not sharing, whatever the
 * total says, and the limit for the group was written for a group that does.
 *
 * @param position where the group stands in the stream, counted from zero
 */
public record GroupFinding(int position, AxleGroup group, Pounds limit) {

    /** The most the heaviest axle of a group may carry over the lightest, in percent of the lightest. */
    public static final int EQUALISED_PERCENT = 10;

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

    /** The heaviest axle of the group less the lightest. */
    public Pounds spread() {
        return group.heaviest().weight().minus(lightest());
    }

    /**
     * Whether the axles of the group share the load the way the limit assumes.
     *
     * <p>A single axle shares with nobody and is always equalised.
     */
    public boolean equalised() {
        long heaviest = group.heaviest().weight().value();
        return heaviest * 100 <= (long) lightest().value() * (100 + EQUALISED_PERCENT);
    }

    private Pounds lightest() {
        return group.axles().stream()
                .map(Axle::weight)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    /** As a line of the letter: {@code group 3, tridem 48,000 lb, allowed 44,000 lb, over by 4,000 lb}. */
    @Override
    public String toString() {
        String verdict = passes() ? "within" : "over by " + over();
        return "group " + (position + 1) + ", " + group.name() + " " + weight() + ", allowed " + limit + ", " + verdict;
    }
}
