package io.github.rkaushikdev.jeepdolly.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The when rules a load carries, whatever route it takes.
 *
 * <p>Width and height put a load in daylight. A superload adds the weekend. A high centre of
 * gravity adds a wind hold, and so does a load broad or tall enough to catch the wind like a sail;
 * neither is a time rule at all, but they ride with them because they belong on the same part of
 * the permit.
 *
 * @param windHoldMph the wind at which the load stops and parks, the lightest of the holds that
 *     apply, for a load with a high centre or a load that is a sail
 * @param reasons why the load is held to daylight, in the order they were found
 */
public record TravelLimits(
        boolean daylightOnly, boolean noWeekend, Optional<Integer> windHoldMph, List<String> reasons) {

    /** A load with no when rules at all. */
    public static final TravelLimits NONE = new TravelLimits(false, false, Optional.empty(), List.of());

    public TravelLimits {
        reasons = List.copyOf(reasons);
    }

    /** Whether the load carries any when rule. */
    public boolean any() {
        return daylightOnly || noWeekend || windHold();
    }

    private boolean windHold() {
        return windHoldMph.filter(mph -> mph != 0).isPresent();
    }

    /** As the planning screen says it: {@code daylight only, no weekend travel}. */
    @Override
    public String toString() {
        if (!any()) {
            return "no travel restriction";
        }
        List<String> parts = new ArrayList<>();
        if (daylightOnly) {
            parts.add("daylight only");
        }
        if (noWeekend) {
            parts.add("no weekend travel");
        }
        if (windHold()) {
            parts.add("hold above " + windHoldMph.get() + " mph wind");
        }
        return String.join(", ", parts);
    }
}
