package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.Segment;

/**
 * One segment of a trip with the way it is driven.
 *
 * @param enteredAt the node the segment is driven from
 */
public record Leg(Segment segment, String enteredAt) {

    /** The node the segment is driven to. */
    public String leftAt() {
        return segment.otherEnd(enteredAt);
    }

    public Miles length() {
        return segment.length();
    }

    /** As a driver reads it: {@code SA 148 norridgewock to canaan}. */
    @Override
    public String toString() {
        return segment.route() + " " + enteredAt + " to " + leftAt();
    }
}
