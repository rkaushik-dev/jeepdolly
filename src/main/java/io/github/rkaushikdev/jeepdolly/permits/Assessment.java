package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.escorts.Escorts;
import io.github.rkaushikdev.jeepdolly.escorts.Marking;
import io.github.rkaushikdev.jeepdolly.escorts.Naming;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.limits.Classification;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.limits.WeightReading;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Mileage;
import io.github.rkaushikdev.jeepdolly.routing.Repair;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import io.github.rkaushikdev.jeepdolly.utilities.LineReading;
import java.util.List;
import java.util.Optional;

/**
 * Everything the office worked out about one application.
 *
 * <p>An assessment is produced whether or not it can be permitted. A refused application still gets
 * the whole assessment, because the letter has to say everything that is wrong rather than the
 * first thing found, and what the hauler could do about the route. Every reading after the refusals
 * is empty when the procedure stopped before it got that far, which is what {@link
 * #Assessment(Application, List)} builds.
 *
 * @param refusals every reason no permit can be written, in the order the procedure found them
 * @param quote the price, present only when the load needs a permit at all
 * @param conditions what the permit prints, in printing order; empty when nothing is issuable
 * @param repair the hauler's own route with the broken part replaced, when the route he wrote is
 *     blocked and one can be found; the refusal stands, and the counter offers this instead of the
 *     router's answer, because the hauler knows where the yard entrance is
 */
public record Assessment(
        Application application,
        List<String> refusals,
        Optional<Itinerary> itinerary,
        Optional<Classification> classification,
        Optional<WeightReading> weight,
        Optional<RouteReading> route,
        Optional<StructureReading> structures,
        Optional<LineReading> lines,
        Optional<Escorts> escorts,
        Optional<Naming> naming,
        Optional<Marking> marking,
        Optional<TravelLimits> limits,
        Optional<Mileage> mileage,
        Optional<Quote> quote,
        List<Condition> conditions,
        Optional<Repair> repair) {

    public Assessment {
        refusals = List.copyOf(refusals);
        conditions = List.copyOf(conditions);
    }

    /** An assessment that stopped before it had anything but the refusals. */
    public Assessment(Application application, List<String> refusals) {
        this(
                application,
                refusals,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty());
    }

    /** Whether a permit can be written: nothing at all was refused. */
    public boolean permittable() {
        return refusals.isEmpty();
    }

    /** The rung of the permit ladder the load reached, or none when it was never classified. */
    public PermitKind kind() {
        return classification.map(Classification::kind).orElse(PermitKind.NONE);
    }

    /** As the queue lists it: {@code 25-04471: ordinary permit} or the count of reasons refused. */
    @Override
    public String toString() {
        if (permittable()) {
            return application.reference() + ": " + kind() + " permit";
        }
        return application.reference() + ": refused, " + refusals.size() + " reasons";
    }
}
