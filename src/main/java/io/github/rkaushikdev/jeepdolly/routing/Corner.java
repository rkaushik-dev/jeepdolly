package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Impossible;

/**
 * One measured turn between two segments at one node, off the office's card index of corners.
 *
 * <p>A hundred and twenty feet of combination will go under everything on US 2 and still not get
 * out of the village at Norridgewock, because the turn from US 2 onto SR 8 is a ninety degree corner
 * with a granite kerb and a bank on the outside. A corner is recorded as the two segments and the
 * node between them, with the radius somebody measured off the plan. What a load needs to get round
 * one is not here: that is the swept path rule in {@link Sweep}, because a corner changes when the
 * town rebuilds a junction and the rule changes when the capital sends an order.
 *
 * <p>A corner is the same corner both ways round. Where a junction really is different in each
 * direction, the office records two corners rather than putting a direction on one.
 *
 * @param into one of the two segments the corner joins
 * @param outOf the other of the two segments the corner joins
 * @param radiusFeet the radius measured off the plan, in whole feet
 * @param note what the card says about it, or empty
 */
public record Corner(String node, String into, String outOf, int radiusFeet, String note) {

    public Corner {
        if (radiusFeet <= 0) {
            throw new Impossible("the corner at " + node + " has a radius");
        }
        if (into.equals(outOf)) {
            throw new Impossible("the corner at " + node + " joins two different segments");
        }
    }

    /** A corner with nothing written on its card but the radius. */
    public Corner(String node, String into, String outOf, int radiusFeet) {
        this(node, into, outOf, radiusFeet, "");
    }

    /** Whether this corner joins the two segments, whichever way it is driven. */
    public boolean joins(String first, String second) {
        return (into.equals(first) && outOf.equals(second)) || (into.equals(second) && outOf.equals(first));
    }

    /** The way the card index reads: {@code skowhegan US201-2 to SA148-2, 40 ft: granite kerb}. */
    @Override
    public String toString() {
        String written = note.isEmpty() ? "" : ": " + note;
        return node + " " + into + " to " + outOf + ", " + radiusFeet + " ft" + written;
    }
}
