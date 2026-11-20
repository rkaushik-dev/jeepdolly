package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Miles;
import java.util.List;

/**
 * A route with the broken part replaced, and what it cost.
 *
 * @param dropped the segments of the written route the repair does not drive, in written order
 * @param added the segments the repair drives that the written route did not, in road order
 */
public record Repair(Itinerary original, Itinerary repaired, List<String> dropped, List<String> added) {

    public Repair {
        dropped = List.copyOf(dropped);
        added = List.copyOf(added);
    }

    /** How much further the repair made the trip; negative when it came out shorter. */
    public Miles extra() {
        return repaired.distance().minus(original.distance());
    }

    /** As the counter reads it out: {@code round US2-1, SA148-1 by SA148-2, 8.00 mi shorter}. */
    @Override
    public String toString() {
        String round = "round " + String.join(", ", dropped) + " by " + String.join(", ", added) + ", ";
        Miles extra = extra();
        if (extra.hundredths() < 0) {
            return round + new Miles(-extra.hundredths()) + " shorter";
        }
        return round + extra + " further";
    }
}
