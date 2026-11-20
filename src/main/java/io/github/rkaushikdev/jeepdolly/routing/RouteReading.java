package io.github.rkaushikdev.jeepdolly.routing;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

/**
 * Everything in the way of one load on one route on one day.
 *
 * @param obstructions every finding, leg by leg in road order, then the corners
 */
public record RouteReading(LocalDate day, Itinerary itinerary, List<Obstruction> obstructions) {

    public RouteReading {
        obstructions = List.copyOf(obstructions);
    }

    /** The findings that mean no permit over this route today. */
    public List<Obstruction> fatal() {
        return obstructions.stream().filter(Obstruction::fatal).toList();
    }

    /** The findings the trip can live with if something is done. */
    public List<Obstruction> conditions() {
        return obstructions.stream().filter(one -> !one.fatal()).toList();
    }

    /** Nothing fatal on the route, whatever conditions it carries. */
    public boolean clear() {
        return fatal().isEmpty();
    }

    /** The segments with something fatal on them, sorted and named once each. */
    public List<String> segmentsInTheWay() {
        TreeSet<String> found = new TreeSet<>();
        fatal().forEach(one -> found.add(one.segment()));
        return List.copyOf(found);
    }

    @Override
    public String toString() {
        if (clear() && conditions().isEmpty()) {
            return "route is clear";
        }
        if (clear()) {
            return "route is clear with " + conditions().size() + " conditions";
        }
        return "route is blocked at " + String.join(", ", segmentsInTheWay());
    }
}
