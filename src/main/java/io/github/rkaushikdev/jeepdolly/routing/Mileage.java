package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.util.ArrayList;
import java.util.List;

/**
 * Chargeable mileage for a whole trip, split by class, with the minimum applied once.
 *
 * <p>The fee is per mile and the mile it is per is a defined thing, not the odometer reading. Four
 * rules stand between the road log and the invoice.
 *
 * <ul>
 *   <li>Only state maintained road is chargeable. A trip that starts in a gravel pit on a town way
 *       and runs four miles to the state road is charged from the state road.
 *   <li>Mileage is charged by class, because the rate differs. The interstate is cheapest per mile
 *       and the state aid roads are dearest, which surprises people until they see what a loaded
 *       lowboy does to a road built in 1954.
 *   <li>Each class is rounded up separately, then added. Rounding the total instead would let a
 *       hauler shave a dollar by splitting the trip across two permits, and the office has seen
 *       that tried.
 *   <li>There is a minimum. A permit for a two mile move costs what a permit for a ten mile move
 *       costs, because the work of writing it is the same and the office is not a toll booth.
 * </ul>
 *
 * @param charged the miles the trip is charged for, the minimum when that applied
 * @param measured the chargeable miles as the road log measures them
 * @param minimumApplied whether the minimum, rather than the trip, set the charge
 */
public record Mileage(List<ClassMiles> parts, int charged, Miles measured, boolean minimumApplied) {

    /** No permit is charged for fewer miles than this. */
    public static final int MINIMUM_MILES = 10;

    public Mileage {
        parts = List.copyOf(parts);
    }

    /** Split the trip by class, round each part up, and apply the usual minimum. */
    public static Mileage chargeable(Itinerary itinerary) {
        return chargeable(itinerary, MINIMUM_MILES);
    }

    /**
     * Split the trip by class, round each part up, and apply the minimum the schedule names.
     *
     * @throws Impossible for a negative minimum
     */
    public static Mileage chargeable(Itinerary itinerary, int minimum) {
        if (minimum < 0) {
            throw new Impossible("a minimum mileage is not negative");
        }
        List<ClassMiles> parts = new ArrayList<>();
        Miles measured = Miles.ZERO;
        int charged = 0;
        for (HighwayClass highwayClass : itinerary.classes()) {
            if (!highwayClass.permittable()) {
                continue;
            }
            Miles distance = itinerary.distanceOn(highwayClass);
            int miles = distance.roundedUp();
            parts.add(new ClassMiles(highwayClass, distance, miles));
            measured = measured.plus(distance);
            charged = charged + miles;
        }
        if (charged >= minimum) {
            return new Mileage(parts, charged, measured, false);
        }
        return new Mileage(parts, minimum, measured, true);
    }

    /** What the hauler drives that this office is not charging for. */
    public static Miles unpermittableMiles(Itinerary itinerary) {
        Miles total = Miles.ZERO;
        for (HighwayClass highwayClass : itinerary.classes()) {
            if (!highwayClass.permittable()) {
                total = total.plus(itinerary.distanceOn(highwayClass));
            }
        }
        return total;
    }

    /** The miles charged on one class of road, nothing for a class the trip does not touch. */
    public int on(HighwayClass highwayClass) {
        return parts.stream()
                .filter(part -> part.highwayClass() == highwayClass)
                .findFirst()
                .map(ClassMiles::charged)
                .orElse(0);
    }

    /** The chargeable classes the trip touched, in the order they are priced. */
    public List<HighwayClass> classes() {
        return parts.stream().map(ClassMiles::highwayClass).toList();
    }

    /** As the quote prints it: {@code 6.20 mi measured, 10 miles charged (minimum)}. */
    @Override
    public String toString() {
        String note = minimumApplied ? " (minimum)" : "";
        return measured + " measured, " + charged + " miles charged" + note;
    }
}
