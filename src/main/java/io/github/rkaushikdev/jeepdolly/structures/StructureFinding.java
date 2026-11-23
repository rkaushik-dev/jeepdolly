package io.github.rkaushikdev.jeepdolly.structures;

import java.util.List;

/**
 * One structure on a route, what it feels of one load, and what that allows.
 *
 * @param window the run of axles the structure carries at once, which is what it is rated on
 */
public record StructureFinding(Structure structure, LoadWindow window, Rating rating) {

    public Verdict verdict() {
        return rating.verdict();
    }

    /** What the permit must say for this crossing, in print order. Nothing unless conditional. */
    public List<String> conditions() {
        return rating.crossingConditions();
    }

    /**
     * As the worksheet reads it: {@code Kennebec River: 94,000 lb of it, 140%}, and why the crossing
     * was held back when it was: {@code Sheepscot River: 40,000 lb of it, 375%, deck 13 ft wide}.
     */
    @Override
    public String toString() {
        String held = rating.held().map(why -> ", " + why).orElse("");
        return structure.name() + ": " + window.weight() + " of it, " + rating.factor() + "%" + held;
    }
}
