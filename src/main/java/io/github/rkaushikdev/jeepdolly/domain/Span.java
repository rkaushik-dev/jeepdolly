package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.List;

/**
 * A run of consecutive axles, which is what the bridge formula asks about.
 *
 * @param first the index of the first axle in the run, from zero
 * @param last the index of the last axle in the run, from zero
 * @param extent first axle to last axle
 */
public record Span(int first, int last, List<Axle> axles, Inches extent) {

    public Span {
        axles = List.copyOf(axles);
    }

    public int count() {
        return axles.size();
    }

    public Pounds weight() {
        Pounds total = Pounds.ZERO;
        for (Axle axle : axles) {
            total = total.plus(axle.weight());
        }
        return total;
    }

    public String name() {
        return AxleGroup.nameFor(count());
    }

    /** The way a refusal letter names it, counting axles from one. */
    @Override
    public String toString() {
        return "axles " + (first + 1) + " to " + (last + 1) + ", " + weight() + " over " + extent;
    }
}
