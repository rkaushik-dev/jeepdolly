package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;

/**
 * One piece of road between two nodes, off the state road log.
 *
 * <p>A segment carries one route designation, has a length in hundredths of a mile, and belongs to
 * one class of road and one town. Most are two way. The one way segments are ramps and a handful of
 * village streets, and a route that runs the wrong way down a ramp is the kind of mistake that gets
 * found by a driver at two in the morning.
 *
 * @param start the node the segment is entered at, when it is one way
 */
public record Segment(
        String ident, Route route, String start, String end, Miles length, Jurisdiction jurisdiction, boolean oneWay) {

    public Segment {
        if (start.equals(end)) {
            throw new Impossible("segment " + ident + " starts and ends in the same place");
        }
        if (length.hundredths() <= 0) {
            throw new Impossible("segment " + ident + " has no length");
        }
    }

    /** A two way segment. */
    public Segment(String ident, Route route, String start, String end, Miles length, Jurisdiction jurisdiction) {
        this(ident, route, start, end, length, jurisdiction, false);
    }

    public HighwayClass highwayClass() {
        return route.highwayClass();
    }

    /** Whether this segment may be entered at {@code node}. */
    public boolean runsFrom(String node) {
        if (node.equals(start)) {
            return true;
        }
        return node.equals(end) && !oneWay;
    }

    /**
     * The end of the segment that is not {@code node}.
     *
     * @throws Impossible when the segment does not touch {@code node}
     */
    public String otherEnd(String node) {
        if (node.equals(start)) {
            return end;
        }
        if (node.equals(end)) {
            return start;
        }
        throw new Impossible("segment " + ident + " does not touch " + node);
    }

    /** The way the road log lists it: {@code SA148-1 SA 148 norridgewock to canaan 9.40 mi}. */
    @Override
    public String toString() {
        return ident + " " + route + " " + start + " to " + end + " " + length;
    }
}
