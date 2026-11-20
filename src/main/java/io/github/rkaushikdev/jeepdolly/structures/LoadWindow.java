package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;

/**
 * The run of axles a structure carries at the worst moment.
 *
 * @param first the index of the first axle on the span, from zero
 * @param last the index of the last axle on the span, from zero
 * @param extent first axle to last axle
 */
public record LoadWindow(int first, int last, Pounds weight, Inches extent) {

    public int count() {
        return last - first + 1;
    }

    /** Whether the run starts with the first axle of the train. */
    public boolean wholeTrain() {
        return first == 0;
    }

    /** As a worksheet line reads: {@code axles 4 to 6, 48,000 lb over 8 ft 8 in}. */
    @Override
    public String toString() {
        return "axles " + (first + 1) + " to " + (last + 1) + ", " + weight + " over " + extent;
    }
}
