package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;

/**
 * What a structure actually feels, which is not what the scale ticket says.
 *
 * <p>A hundred and eighty thousand pound combination is ninety feet long. A forty eight foot single
 * span bridge never has all of it on at once. So the load on a span is the heaviest run of
 * consecutive axles that fits within the span length: nine axles on a four hundred foot bridge is
 * all nine, and the same nine on a forty eight foot culvert is whichever four of them are worst.
 *
 * <p>Two details matter and both have bitten somebody. The window is measured axle to axle, not
 * bumper to bumper: an axle sitting one inch past the abutment is off the span, and the office does
 * not give itself credit for the inch. And the worst window is not always at the back, because a
 * jeep dolly puts four heavy axles at the front, so it is searched for rather than assumed.
 *
 * <p>For a multi span structure the governing length is the longest single span. That is
 * conservative for continuous spans and exact for the simple spans most of the inventory is made
 * of.
 */
public final class Loading {

    private Loading() {}

    /**
     * The heaviest run of axles that fits inside a span of this length, the first of them on a tie.
     *
     * @throws Impossible for a span of no length
     */
    public static LoadWindow loadOnSpan(AxleStream stream, int spanFeet) {
        if (spanFeet <= 0) {
            throw new Impossible("a span has a length");
        }
        Inches reach = Inches.of(spanFeet);
        LoadWindow best = new LoadWindow(0, 0, stream.axles().get(0).weight(), Inches.ZERO);
        for (int first = 0; first < stream.count(); first++) {
            for (int last = first; last < stream.count(); last++) {
                Inches extent = stream.distance(first, last);
                if (extent.compareTo(reach) > 0) {
                    break;
                }
                Pounds weight = Pounds.ZERO;
                for (int index = first; index <= last; index++) {
                    weight = weight.plus(stream.axles().get(index).weight());
                }
                if (weight.compareTo(best.weight()) > 0) {
                    best = new LoadWindow(first, last, weight, extent);
                }
            }
        }
        return best;
    }

    /**
     * What this structure feels, governed by its longest single span.
     *
     * @throws Impossible for a structure that carries no traffic
     */
    public static LoadWindow loadOn(Structure structure, AxleStream stream) {
        if (!structure.kind().carriesTraffic()) {
            throw new Impossible(structure.ident() + " does not carry traffic");
        }
        return loadOnSpan(stream, structure.longestSpan());
    }

    /** The weight to rate this structure against. */
    public static Pounds governingWeight(Structure structure, AxleStream stream) {
        return loadOn(structure, stream).weight();
    }

    /** How much of the combination the structure never sees at once. */
    public static Pounds relief(Structure structure, AxleStream stream) {
        return stream.gross().minus(governingWeight(structure, stream));
    }
}
